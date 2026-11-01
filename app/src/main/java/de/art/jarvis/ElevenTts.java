package de.art.jarvis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Premium-Stimme über ElevenLabs. Optional: Ohne Schlüssel – oder wenn ElevenLabs streikt –
 * spricht Jarvis mit der Stimme des Handys weiter.
 */
final class ElevenTts {
    /** Standard: „Daniel“ – britisch, tief, ruhig und präzise (ElevenLabs-Fertigstimme). */
    static final String DEFAULT_VOICE = "onwK4e9ZLuTAKqWW03F9";
    /** Schnellstes ElevenLabs-Modell mit Deutsch (vorher „multilingual_v2“: natürlicher, aber deutlich langsamer). */
    static final String MODEL = "eleven_flash_v2_5";

    interface Listener {
        void onStart();
        void onDone();
        void onError(String message);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private int generation;

    ElevenTts(Context context) {
        this.context = context.getApplicationContext();
    }

    void speak(String apiKey, String voiceId, String text, float rate, Listener listener) {
        stop();
        final int gen = ++generation;
        final String voice = voiceId == null || voiceId.trim().isEmpty() ? DEFAULT_VOICE : voiceId.trim();
        final float speed = Math.max(0.7f, Math.min(1.2f, rate));
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject settings = new JSONObject()
                        .put("stability", 0.6)
                        .put("similarity_boost", 0.8)
                        .put("speed", speed);
                JSONObject body = new JSONObject()
                        .put("text", text)
                        .put("model_id", MODEL)
                        .put("voice_settings", settings);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

                conn = (HttpURLConnection) new URL("https://api.elevenlabs.io/v1/text-to-speech/" + voice
                        + "?output_format=mp3_44100_128").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(payload.length);
                conn.setRequestProperty("content-type", "application/json");
                conn.setRequestProperty("accept", "audio/mpeg");
                conn.setRequestProperty("xi-api-key", apiKey);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    final String msg = describeError(code, readText(conn.getErrorStream()));
                    main.post(() -> {
                        if (gen == generation) listener.onError(msg);
                    });
                    return;
                }
                File out = new File(context.getCacheDir(), "jarvis_stimme.mp3");
                try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                }
                main.post(() -> {
                    if (gen == generation) play(out, listener);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (gen == generation) listener.onError("ElevenLabs nicht erreichbar.");
                });
            }
            // Kein disconnect(): Verbindung bleibt offen und wird wiederverwendet.
        });
    }

    private void play(File file, Listener listener) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnCompletionListener(p -> {
                release();
                listener.onDone();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                release();
                listener.onError("Wiedergabe fehlgeschlagen.");
                return true;
            });
            mp.prepare();
            player = mp;
            mp.start();
            listener.onStart();
        } catch (Exception e) {
            release();
            listener.onError("Wiedergabe fehlgeschlagen.");
        }
    }

    boolean isSpeaking() {
        try {
            return player != null && player.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    /** Bricht Laden und Wiedergabe ab (z. B. wenn Art aufs J tippt). */
    void stop() {
        generation++;
        release();
    }

    private void release() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
                // war noch nicht gestartet
            }
            player.release();
            player = null;
        }
    }

    private static String describeError(int code, String body) {
        String lower = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("quota")) return "ElevenLabs-Kontingent für diesen Monat aufgebraucht.";
        if (code == 401) return "ElevenLabs-Schlüssel wird abgelehnt.";
        if (code == 404 || lower.contains("voice_not_found")) return "ElevenLabs-Stimme nicht gefunden – Stimmen-ID prüfen.";
        if (code == 429) return "ElevenLabs ist gerade ausgelastet.";
        return "ElevenLabs-Fehler " + code + ".";
    }

    private static String readText(InputStream in) {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
