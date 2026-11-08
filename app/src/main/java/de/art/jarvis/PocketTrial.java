package de.art.jarvis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.k2fsa.sherpa.onnx.GeneratedAudio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * „Offline-Stimme ausprobieren“ in den Einstellungen (Machbarkeit Pocket TTS, 8. Nov.): lädt beim ersten Mal das
 * deutsche Modell und eine Sprachprobe (zusammen rund 147 MB, jede Datei mit fester Prüfsumme), lädt die Stimme,
 * spricht drei Sätze und misst dabei die Zeiten. Erst diese Zahlen vom S24 Ultra zeigen, ob die Offline-Stimme
 * schnell genug ist, um ElevenLabs zu ersetzen – der Emulator in der CI ist dafür viel zu langsam.
 *
 * <p>Geladen wird nur, wenn Art den Knopf drückt. Danach liegt alles im App-Ordner „pocket“.
 */
final class PocketTrial {
    interface Listener {
        /** Zwischenstand oder Ergebnis, immer auf dem Haupt-Thread. */
        void onUpdate(String text, boolean done);
    }

    /** Feste Fassung des Modell-Repos – eine spätere Änderung dort kann nichts Ungeprüftes einschleusen. */
    private static final String MODEL_BASE = "https://huggingface.co/haex-space/pocket-tts-sherpa-onnx/resolve/"
            + "d8f5915ed8bd94e1aa3dbd5a219360b3bb966190/de/";
    /** Sprachprobe: VCTK-Sprecher p226 (britisch, männlich, CC BY 4.0) aus Kyutais Stimmen-Sammlung. */
    private static final String VOICE_URL =
            "https://huggingface.co/kyutai/tts-voices/resolve/main/vctk/p226_023_enhanced.wav";

    /** Datei, Größe in Bytes, SHA-256. */
    private static final String[][] FILES = {
            {"lm_main.int8.onnx", "76341079", "3d5e9cd303ecd26fc75b91e2766bce6de3d274d397d3ee9a392f6299366681ef"},
            {"decoder.int8.onnx", "22684077", "3fe868810d66fa0c26bb3ae0d5ee65cae83028c0fe83398bcf3d96478f68083b"},
            {"encoder.onnx", "20783791", "c5a02b66a1720396da7edeb252aa3080603e4b079b87c5e2ffcb74c95aba2912"},
            {"text_conditioner.onnx", "16388384", "de27b06fcd3901d50fdd95d669ba7a59e8311e4dba4120635eca3f3a898871b7"},
            {"lm_flow.int8.onnx", "9962530", "2b0eb8381c49672ac1050c50221db02033b5dc6c346ab45c67bd05344257f06c"},
            {"token_scores.json", "124036", "cd446b01d12e68f951757bdc703fc96c31ecefa0b186b4adf569a74c571161a6"},
            {"vocab.json", "69903", "c78ab36f541fdf5971472ca589a4619a3173522680b2fd58d34c5dd443f214fc"},
            {"stimme.wav", "777836", "24d12e5ffae86407fd8190448bb630c2ce60dc955ad6254dd7262d4aaf1b1f72"},
    };

    static final String[] SENTENCES = {
            "Zu Diensten, Sir.",
            "Kaufen Sie jetzt den zweiten Ornn und stellen Sie Ihren Carry hinten in die Ecke.",
            "Morgen um neun Uhr haben Sie einen Termin beim Zahnarzt.",
    };

    private static volatile boolean running;

    private PocketTrial() {}

    static File dir(Context c) {
        return new File(c.getFilesDir(), "pocket");
    }

    /** Liegt schon alles auf dem Handy (dann lädt der Knopf nichts mehr)? */
    static boolean downloaded(Context c) {
        File d = dir(c);
        for (String[] f : FILES) {
            if (new File(d, f[0]).length() != Long.parseLong(f[1])) return false;
        }
        return true;
    }

    /** Rund 147 MB – für den Hinweis am Knopf. */
    static long totalBytes() {
        long sum = 0;
        for (String[] f : FILES) sum += Long.parseLong(f[1]);
        return sum;
    }

    static void start(Context context, Listener listener) {
        if (running) return;
        running = true;
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            PocketVoice voice = null;
            try {
                File d = dir(app);
                if (!d.isDirectory() && !d.mkdirs()) throw new IOException("Ordner lässt sich nicht anlegen");
                download(d, (text) -> main.post(() -> listener.onUpdate(text, false)));
                main.post(() -> listener.onUpdate("Offline-Stimme wird geladen …", false));
                int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
                long t0 = SystemClock.elapsedRealtime();
                voice = new PocketVoice(d, new File(d, "stimme.wav"), threads);
                StringBuilder report = new StringBuilder();
                report.append(String.format(Locale.GERMANY, "Offline-Stimme (Pocket TTS, %d Threads)\nGeladen in %.1f s\n",
                        threads, (SystemClock.elapsedRealtime() - t0) / 1000f));
                for (int i = 0; i < SENTENCES.length; i++) {
                    final String now = report + "Spreche Satz " + (i + 1) + " …";
                    main.post(() -> listener.onUpdate(now, false));
                    long t = SystemClock.elapsedRealtime();
                    GeneratedAudio audio = voice.generate(SENTENCES[i]);
                    long ms = SystemClock.elapsedRealtime() - t;
                    float sec = audio.getSamples().length / (float) audio.getSampleRate();
                    report.append(String.format(Locale.GERMANY, "Satz %d: %.2f s Rechenzeit für %.1f s Sprache%s\n",
                            i + 1, ms / 1000f, sec, i == 0 ? " (mit Einlesen der Stimme)" : ""));
                    PcmPlayer.play(audio.getSamples(), audio.getSampleRate());
                }
                report.append("Unter 1 s je kurzem Satz = schnell genug für Jarvis.");
                final String result = report.toString();
                main.post(() -> listener.onUpdate(result, true));
            } catch (Throwable e) {
                final String msg = "✗ Offline-Stimme: " + e.getMessage();
                main.post(() -> listener.onUpdate(msg, true));
            } finally {
                if (voice != null) voice.release();
                running = false;
            }
        }, "pocket-trial").start();
    }

    private interface Progress {
        void report(String text);
    }

    private static void download(File dir, Progress progress) throws IOException {
        long total = totalBytes();
        long done = 0;
        for (String[] f : FILES) {
            File target = new File(dir, f[0]);
            long size = Long.parseLong(f[1]);
            if (target.length() == size) {
                done += size;
                continue;
            }
            String url = f[0].equals("stimme.wav") ? VOICE_URL : MODEL_BASE + f[0];
            File part = new File(dir, f[0] + ".part");
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new IOException("SHA-256 fehlt");
            }
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("Download " + f[0] + ": HTTP " + code);
            long fileDone = 0;
            long lastReport = 0;
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    fileDone += n;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastReport > 500) {
                        lastReport = now;
                        progress.report(String.format(Locale.GERMANY, "Lade Offline-Stimme: %d von %d MB …",
                                (done + fileDone) >> 20, total >> 20));
                    }
                }
            } finally {
                conn.disconnect();
            }
            String got = hex(md.digest());
            if (!got.equals(f[2])) {
                part.delete();
                throw new IOException("Prüfsumme von " + f[0] + " stimmt nicht – Datei verworfen");
            }
            if (!part.renameTo(target)) throw new IOException("Speichern von " + f[0] + " fehlgeschlagen");
            done += size;
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }
}
