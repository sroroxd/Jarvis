package de.art.jarvis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.art.jarvis.wake.WakeWordDetector;

/**
 * Hört offline auf „Jarvis“: eigenes Mikrofon (16 kHz) → {@link WakeWordDetector}.
 *
 * <p>Nach einem Treffer gibt die Engine das Mikrofon sofort frei, damit die Spracherkennung
 * ohne Verzögerung übernehmen kann. Alle öffentlichen Methoden laufen auf dem Haupt-Thread.
 */
final class WakeWordEngine {
    interface Listener {
        void onWakeWord();

        void onWakeError(String message);
    }

    private static final String DIR = "wakeword/";
    /** Nach einem Treffer kurz nichts melden, falls dasselbe „Jarvis“ noch nachhallt. */
    private static final long COOLDOWN_MS = 1500;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> afterStop = new ArrayList<>();

    private Thread thread;
    private volatile boolean active;
    private volatile float threshold = Prefs.thresholdFor(Prefs.DEFAULT_SENSITIVITY);
    private boolean restart;
    private boolean releaseRequested;
    private long lastDetection;
    /** Wird nur vom Arbeits-Thread angefasst (und nach dessen Ende vom Haupt-Thread geschlossen). */
    private WakeWordDetector detector;

    WakeWordEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void setSensitivity(int sensitivity) {
        threshold = Prefs.thresholdFor(sensitivity);
    }

    boolean isRunning() {
        return thread != null && active;
    }

    /** Startet das Zuhören (macht nichts, wenn es schon läuft). */
    void start() {
        releaseRequested = false;
        if (thread != null) {
            if (!active) restart = true; // läuft gerade aus → danach sofort wieder starten
            return;
        }
        active = true;
        restart = false;
        Thread t = new Thread(this::loop, "jarvis-wachwort");
        thread = t;
        t.start();
    }

    /** Hält an und gibt das Mikrofon frei; {@code then} läuft, sobald es wirklich frei ist. */
    void stop(Runnable then) {
        restart = false;
        if (thread == null) {
            if (then != null) then.run();
            return;
        }
        active = false;
        if (then != null) afterStop.add(then);
    }

    /** Hält an und gibt die Modelle frei (beim Beenden von Jarvis). */
    void release() {
        releaseRequested = true;
        stop(null);
        if (thread == null) closeDetector();
    }

    // ------------------------------------------------------------ Arbeits-Thread

    @SuppressLint("MissingPermission") // wird vom Dienst vor dem Start geprüft
    private void loop() {
        boolean detected = false;
        String error = null;
        AudioRecord record = null;
        try {
            if (detector == null) detector = load(context.getAssets());
            detector.reset();
            int min = AudioRecord.getMinBufferSize(WakeWordDetector.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min, WakeWordDetector.CHUNK * 2 * 4);
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, WakeWordDetector.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("Mikrofon ist belegt oder nicht verfügbar");
            }
            record.startRecording();
            short[] chunk = new short[WakeWordDetector.CHUNK];
            while (active) {
                int filled = 0;
                while (filled < chunk.length && active) {
                    int n = record.read(chunk, filled, chunk.length - filled);
                    if (n < 0) throw new IllegalStateException("Mikrofon meldet Fehler " + n);
                    filled += n;
                }
                if (!active) break;
                float score = detector.process(chunk);
                long now = android.os.SystemClock.elapsedRealtime();
                if (score >= threshold && now - lastDetection > COOLDOWN_MS) {
                    lastDetection = now;
                    detected = true;
                    active = false; // Mikrofon sofort für die Spracherkennung freigeben
                }
            }
        } catch (Throwable e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            if (record != null) {
                try {
                    record.stop();
                } catch (Exception ignored) {
                    // war nicht gestartet
                }
                record.release();
            }
        }
        final boolean hit = detected;
        final String failure = error;
        main.post(() -> finished(hit, failure));
    }

    /** Läuft auf dem Haupt-Thread, nachdem der Arbeits-Thread das Mikrofon freigegeben hat. */
    private void finished(boolean hit, String error) {
        thread = null;
        active = false;
        List<Runnable> callbacks = new ArrayList<>(afterStop);
        afterStop.clear();
        for (Runnable r : callbacks) r.run();
        if (releaseRequested) {
            closeDetector();
            return;
        }
        if (error != null) {
            restart = false;
            listener.onWakeError(error);
            return;
        }
        if (hit) {
            restart = false;
            listener.onWakeWord();
            return;
        }
        if (restart) start();
    }

    private void closeDetector() {
        if (detector != null) {
            detector.close();
            detector = null;
        }
    }

    /** Lädt die drei Modelle. Auch fürs Dazwischenreden ({@link BargeIn}), das einen eigenen Erkenner hält. */
    static WakeWordDetector load(AssetManager assets) throws Exception {
        return new WakeWordDetector(
                read(assets, DIR + "melspectrogram.onnx"),
                read(assets, DIR + "embedding_model.onnx"),
                // Eigenes Modell (training/wakeword): erkennt „Jarvis“ allein, englisch und deutsch ausgesprochen,
                // und „Hey/Okay Jarvis“. Das alte hey_jarvis-Modell zusätzlich brachte in Messungen keinen Treffer mehr,
                // nur mehr Fehlalarme – deshalb nur dieses eine.
                read(assets, DIR + "jarvis.onnx"));
    }

    private static byte[] read(AssetManager assets, String name) throws IOException {
        try (InputStream in = assets.open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
