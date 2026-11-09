package de.art.jarvis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
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
    private File playingFile;
    /** Im Telefonmodus (Dauergespräch) als Sprachkommunikation abspielen, damit die Echo-Unterdrückung greift. */
    private int usage = AudioAttributes.USAGE_ASSISTANT;
    private ElevenDownload download;

    ElevenTts(Context context) {
        this.context = context.getApplicationContext();
    }

    void speak(String apiKey, String voiceId, String text, float rate, Listener listener) {
        stop();
        final ElevenDownload current = new ElevenDownload();
        download = current;
        final String voice = voiceId == null || voiceId.trim().isEmpty() ? DEFAULT_VOICE : voiceId.trim();
        final float speed = Math.max(0.7f, Math.min(1.2f, rate));
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

            current.start(executor, () -> {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://api.elevenlabs.io/v1/text-to-speech/" + voice
                        + "?output_format=mp3_44100_128").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(payload.length);
                conn.setRequestProperty("content-type", "application/json");
                conn.setRequestProperty("accept", "audio/mpeg");
                conn.setRequestProperty("xi-api-key", apiKey);
                return conn;
            }, payload, context.getCacheDir(), new ElevenDownload.Listener() {
                @Override
                public void onAudio(File out) {
                    if (!main.post(() -> {
                        if (isCurrent(current)) play(out, current, listener);
                        else out.delete();
                    })) out.delete();
                }

                @Override
                public void onHttpError(int code, String body) {
                    final String msg = describeError(code, body);
                    main.post(() -> {
                        if (isCurrent(current)) listener.onError(msg);
                    });
                }

                @Override
                public void onFailure() {
                    main.post(() -> {
                        if (isCurrent(current)) listener.onError("ElevenLabs nicht erreichbar.");
                    });
                }
            });
        } catch (Exception e) {
            if (isCurrent(current)) listener.onError("ElevenLabs nicht erreichbar.");
        }
    }

    private boolean isCurrent(ElevenDownload request) {
        return download == request && !request.cancelled();
    }

    private void play(File file, ElevenDownload request, Listener listener) {
        playingFile = file;
        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnCompletionListener(p -> {
                if (p != player || !isCurrent(request)) return;
                release();
                listener.onDone();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                if (p != player || !isCurrent(request)) return true;
                release();
                listener.onError("Wiedergabe fehlgeschlagen.");
                return true;
            });
            mp.prepare();
            mp.start();
            if (isCurrent(request)) listener.onStart();
        } catch (Exception e) {
            release();
            if (isCurrent(request)) listener.onError("Wiedergabe fehlgeschlagen.");
        }
    }

    void setVoiceCall(boolean on) {
        usage = on ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_ASSISTANT;
    }

    /** Bricht Laden und Wiedergabe ab (z. B. wenn Art aufs J tippt). */
    void stop() {
        ElevenDownload old = download;
        download = null;
        if (old != null) old.cancel();
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
        if (playingFile != null) {
            playingFile.delete();
            playingFile = null;
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

}
