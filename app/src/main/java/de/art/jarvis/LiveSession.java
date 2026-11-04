package de.art.jarvis;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Eine laufende Gesprächssitzung mit der Gemini Live API.
 *
 * Aufbau: Mikrofon → WebSocket → Modell → Lautsprecher, alles gleichzeitig.
 * Die Sitzung hält sich selbst am Leben: Google trennt eine Verbindung nach
 * etwa zehn Minuten und kündigt das vorher mit {@code goAway} an. Dann wird
 * mit dem gespeicherten Wiederaufnahme-Griff neu verbunden, ohne dass der
 * Gesprächsverlauf verloren geht – Art merkt davon nichts.
 */
final class LiveSession {
    private static final String TAG = "JarvisLiveSession";

    /** Standardmodell: Googles Vorgabe für Sprachassistenten mit geringer Verzögerung. */
    static final String DEFAULT_MODEL = "gemini-3.8-live";

    private static final int MAX_RETRIES = 5;
    /** Kurz vor dem angekündigten Trennen neu verbinden, nicht erst danach. */
    private static final long RECONNECT_LEAD_MS = 3000;

    interface Listener {
        /** Verbindung steht und das Mikrofon läuft. */
        void onReady();
        /** Was Art gesagt hat (Transkript). */
        void onUserText(String text);
        /** Was Jarvis geantwortet hat (Transkript). */
        void onJarvisText(String text);
        /** Jarvis hat zu sprechen begonnen bzw. aufgehört. */
        void onSpeaking(boolean speaking);
        /** Klartext-Problem für Art. */
        void onProblem(String german);
        /** Sitzung ist zu Ende. */
        void onEnded();
    }

    private final Context context;
    private final String apiKey;
    private final String model;
    private final String voice;
    private final String systemText;
    private final Listener listener;

    private final OkHttpClient http;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final LiveCost cost = new LiveCost();

    private LiveAudio audio;
    private volatile WebSocket socket;
    /** Zählt Verbindungen: Rückmeldungen alter Verbindungen werden ignoriert. */
    private volatile int epoch;
    private volatile boolean stopped;
    private volatile boolean ready;
    private volatile boolean wasSpeaking;
    private volatile String resumeHandle;
    private int retries;
    /** Zeitpunkt, ab dem Bildschirmbilder gezählt werden; 0 = kein Bild. */
    private volatile long videoSince;

    LiveSession(Context context, String apiKey, String model, String voice,
                String systemText, Listener listener) {
        this.context = context;
        this.apiKey = apiKey;
        this.model = model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim();
        this.voice = voice;
        this.systemText = systemText;
        this.listener = listener;
        this.http = new OkHttpClient.Builder()
                // Eine Gesprächsverbindung steht lange still, wenn niemand redet.
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    /** @return false, wenn schon das Mikrofon nicht aufgeht. */
    boolean start() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            listener.onProblem("Für den Gesprächsmodus fehlt der Google-Schlüssel in den Einstellungen.");
            return false;
        }
        cost.reset();
        LiveAudio a = new LiveAudio(context, this::onMicChunk, listener::onProblem);
        if (!a.start()) {
            // a.start() hat sich selbst schon aufgeräumt und den Grund gemeldet.
            return false;
        }
        audio = a;
        connect();
        return true;
    }

    private void connect() {
        if (stopped) return;
        final int mine = ++epoch;
        String url = LiveProtocol.ENDPOINT + "?key=" + apiKey.trim();
        Request request = new Request.Builder().url(url).build();
        socket = http.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                if (mine != epoch || stopped) return;
                try {
                    ws.send(LiveProtocol.setupMessage(model, voice, systemText, resumeHandle));
                } catch (Exception e) {
                    Log.e(TAG, "Setup ging nicht raus", e);
                    listener.onProblem("Die Sitzung ließ sich nicht einrichten.");
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                if (mine == epoch && !stopped) handle(text);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                // Google schickt dasselbe JSON teils als Binärblock.
                if (mine == epoch && !stopped) handle(bytes.utf8());
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                if (mine != epoch || stopped) return;
                Log.i(TAG, "Verbindung zu (" + code + ") " + reason);
                retry("Die Verbindung wurde getrennt (" + code + ").");
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                if (mine != epoch || stopped) return;
                int code = response != null ? response.code() : 0;
                Log.w(TAG, "Verbindung gescheitert, Code " + code, t);
                if (code == 401 || code == 403) {
                    listener.onProblem("Der Google-Schlüssel wird abgelehnt. Prüf ihn in den Einstellungen.");
                    stop();
                } else if (code == 429) {
                    listener.onProblem("Google-Kontingent erschöpft. Für Dauerbetrieb braucht der Schlüssel die bezahlte Stufe.");
                    stop();
                } else {
                    retry("Keine Verbindung zu Google.");
                }
            }
        });
    }

    private void handle(String json) {
        // Fehlerantworten von Google sind gültiges JSON – der Parser würde sie
        // stillschweigend als leere Nachricht durchlassen. Deshalb zuerst prüfen,
        // sonst sieht Art beim ersten Verbindungsversuch nur Schweigen.
        if (json != null && json.contains("\"error\"")) {
            String server = serverError(json);
            Log.w(TAG, "Google meldet Fehler: " + cut(json));
            listener.onProblem(server != null
                    ? "Google meldet: " + server
                    : "Google hat die Sitzung abgelehnt.");
            return;
        }

        LiveProtocol.Event e;
        try {
            e = LiveProtocol.parse(json);
        } catch (Exception ex) {
            Log.w(TAG, "Nachricht unlesbar: " + cut(json), ex);
            return;
        }

        if (e.setupComplete) {
            ready = true;
            retries = 0;
            listener.onReady();
        }

        if (e.interrupted) {
            // Art hat dazwischengeredet.
            if (audio != null) audio.dropPlayback();
            setSpeaking(false);
        }

        if (e.audio.length > 0) {
            cost.addPlayback(LiveCost.pcmMs(e.audio.length, LiveAudio.OUT_RATE));
            if (audio != null) audio.play(e.audio);
            setSpeaking(true);
        }

        if (e.inputText != null && !e.inputText.isEmpty()) listener.onUserText(e.inputText);
        if (e.outputText != null && !e.outputText.isEmpty()) listener.onJarvisText(e.outputText);

        if (e.turnComplete) setSpeaking(false);

        if (e.resumeHandle != null) resumeHandle = e.resumeHandle;

        if (e.goAwayMs >= 0) {
            long in = Math.max(0, e.goAwayMs - RECONNECT_LEAD_MS);
            Log.i(TAG, "Verbindung endet in " + e.goAwayMs + " ms, neu verbinden in " + in + " ms");
            timer.schedule(this::reconnect, in, TimeUnit.MILLISECONDS);
        }
    }

    private void setSpeaking(boolean speaking) {
        if (speaking == wasSpeaking) return;
        wasSpeaking = speaking;
        listener.onSpeaking(speaking);
    }

    private void onMicChunk(byte[] pcm) {
        WebSocket ws = socket;
        if (ws == null || !ready || stopped) return;
        try {
            if (ws.send(LiveProtocol.audioChunk(pcm))) {
                cost.addMic(LiveCost.pcmMs(pcm.length, LiveAudio.IN_RATE));
            }
        } catch (Exception e) {
            Log.w(TAG, "Mikrofonblock ging nicht raus", e);
        }
    }

    /** Ein Bildschirmbild mitschicken (Spielmodus). */
    void sendScreen(byte[] jpeg) {
        WebSocket ws = socket;
        if (ws == null || !ready || stopped || jpeg == null || jpeg.length == 0) return;
        try {
            if (!ws.send(LiveProtocol.videoFrame(jpeg))) return;
            long now = System.currentTimeMillis();
            long since = videoSince;
            // Bild wird nach Minuten abgerechnet: die Zeit zwischen zwei Bildern zählen,
            // Pausen über zwei Sekunden gelten als „kein Bild“.
            if (since > 0 && now - since <= 2000) cost.addVideo(now - since);
            videoSince = now;
        } catch (Exception e) {
            Log.w(TAG, "Bildschirmbild ging nicht raus", e);
        }
    }

    private void reconnect() {
        if (stopped) return;
        ready = false;
        WebSocket old = socket;
        socket = null;
        if (old != null) {
            try { old.close(1000, "Neuverbindung"); } catch (Exception ignored) { }
        }
        connect();
    }

    private void retry(String germanIfGivingUp) {
        if (stopped) return;
        ready = false;
        if (++retries > MAX_RETRIES) {
            listener.onProblem(germanIfGivingUp + " Gesprächsmodus beendet.");
            stop();
            return;
        }
        long wait = Math.min(8000, 500L * (1L << (retries - 1)));
        Log.i(TAG, "Neuer Versuch " + retries + " in " + wait + " ms");
        timer.schedule(this::reconnect, wait, TimeUnit.MILLISECONDS);
    }

    void stop() {
        if (stopped) return;
        stopped = true;
        ready = false;
        epoch++;

        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            try {
                ws.send(LiveProtocol.audioStreamEnd());
            } catch (Exception ignored) {
            }
            try { ws.close(1000, "Ende"); } catch (Exception ignored) { }
        }

        LiveAudio a = audio;
        audio = null;
        if (a != null) a.stop();

        timer.shutdownNow();
        listener.onEnded();
    }

    boolean running() { return !stopped; }

    /** Zeile für den Startbildschirm: Dauer und geschätzte Kosten. */
    String costLine() { return cost.summary(); }

    /** Ob die Echo-Unterdrückung auf diesem Gerät angelaufen ist. */
    boolean aecActive() {
        LiveAudio a = audio;
        return a != null && a.aecActive();
    }

    /** Googles Fehlermeldung aus einer Antwort ziehen, ohne eine Bibliothek dafür. */
    private static String serverError(String json) {
        if (json == null) return null;
        int at = json.indexOf("\"message\"");
        if (at < 0) return null;
        int start = json.indexOf('"', json.indexOf(':', at) + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        String message = json.substring(start + 1, end).trim();
        return message.isEmpty() ? null : cut(message);
    }

    private static String cut(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
