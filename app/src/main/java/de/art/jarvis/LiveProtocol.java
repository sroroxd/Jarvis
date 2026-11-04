package de.art.jarvis;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Nachrichten-Protokoll der Gemini Live API (WebSocket, BidiGenerateContent).
 * Reine Java-Logik ohne Netz und ohne Mikrofon – dadurch unit-testbar.
 *
 * Endpunkt und Feldnamen laut Google-Doku (ai.google.dev/api/live):
 * Client-Nachrichten: setup | clientContent | realtimeInput | toolResponse
 * Server-Nachrichten: setupComplete | serverContent | toolCall | goAway | sessionResumptionUpdate
 */
final class LiveProtocol {
    static final String ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";

    /** Eingabe: 16 kHz, 16 Bit, mono, little-endian (Doku Firebase/Live API). */
    static final String MIME_AUDIO_IN = "audio/pcm;rate=16000";
    /** Ausgabe-Audio kommt als 24 kHz PCM zurück. */
    static final int OUTPUT_SAMPLE_RATE = 24000;

    /** Standardstimme; Gemini-Stimmen heißen z. B. Charon, Fenrir, Orus, Puck, Kore, Aoede. */
    static final String DEFAULT_VOICE = "Charon";

    private LiveProtocol() {}

    /**
     * Erste Nachricht nach dem Verbindungsaufbau: Modell, Stimme, Systemanweisung,
     * Transkription, Kontext-Kompression (ohne sie endet eine Video-Sitzung nach 2 Minuten)
     * und Wiederaufnahme. {@code resumeHandle} null = neue Sitzung.
     */
    static String setupMessage(String model, String voice, String systemText, String resumeHandle) throws Exception {
        JSONObject speech = new JSONObject()
                .put("voiceConfig", new JSONObject()
                        .put("prebuiltVoiceConfig", new JSONObject()
                                .put("voiceName", voice == null || voice.isEmpty() ? DEFAULT_VOICE : voice)));

        JSONObject generation = new JSONObject()
                .put("responseModalities", new JSONArray().put("AUDIO"))
                .put("speechConfig", speech);

        JSONObject setup = new JSONObject()
                .put("model", "models/" + model)
                .put("generationConfig", generation)
                .put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", systemText))))
                .put("inputAudioTranscription", new JSONObject())
                .put("outputAudioTranscription", new JSONObject())
                .put("contextWindowCompression", new JSONObject().put("slidingWindow", new JSONObject()))
                .put("sessionResumption", resumeHandle == null
                        ? new JSONObject()
                        : new JSONObject().put("handle", resumeHandle));

        return new JSONObject().put("setup", setup).toString();
    }

    /** Ein Mikrofon-Block (PCM 16 kHz, 16 Bit mono). */
    static String audioChunk(byte[] pcm16k) throws Exception {
        return realtime(new JSONObject()
                .put("audio", new JSONObject()
                        .put("mimeType", MIME_AUDIO_IN)
                        .put("data", Base64.getEncoder().encodeToString(pcm16k))));
    }

    /** Ein Bildschirmbild als JPEG. */
    static String videoFrame(byte[] jpeg) throws Exception {
        return realtime(new JSONObject()
                .put("video", new JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", Base64.getEncoder().encodeToString(jpeg))));
    }

    /** Textnachricht (z. B. für Tests oder eine getippte Frage im Gesprächsmodus). */
    static String text(String text) throws Exception {
        return realtime(new JSONObject().put("text", text));
    }

    /** Signal „Mikrofon aus“ – das Modell weiß dann, dass kein weiteres Audio kommt. */
    static String audioStreamEnd() throws Exception {
        return realtime(new JSONObject().put("audioStreamEnd", true));
    }

    private static String realtime(JSONObject inner) throws Exception {
        return new JSONObject().put("realtimeInput", inner).toString();
    }

    /** Ergebnis einer Server-Nachricht. Eine Nachricht kann mehrere Teile enthalten. */
    static final class Event {
        boolean setupComplete;
        /** Zusammengefügtes Ausgabe-Audio (PCM 24 kHz, 16 Bit mono) dieser Nachricht. */
        byte[] audio = new byte[0];
        boolean turnComplete;
        /** Der Nutzer hat unterbrochen – laufende Ausgabe sofort stoppen. */
        boolean interrupted;
        String inputText;
        String outputText;
        /** Neuer Wiederaufnahme-Griff, falls vorhanden. */
        String resumeHandle;
        /** Restzeit bis zum Verbindungsende in Millisekunden, sonst -1. */
        long goAwayMs = -1;
        boolean toolCall;
    }

    /** Zerlegt eine Server-Nachricht. Unbekannte Felder werden ignoriert. */
    static Event parse(String json) throws Exception {
        JSONObject msg = new JSONObject(json);
        Event e = new Event();

        if (msg.has("setupComplete")) e.setupComplete = true;

        JSONObject sc = msg.optJSONObject("serverContent");
        if (sc != null) {
            JSONObject turn = sc.optJSONObject("modelTurn");
            if (turn != null) {
                JSONArray parts = turn.optJSONArray("parts");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (parts != null) {
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject inline = parts.getJSONObject(i).optJSONObject("inlineData");
                        if (inline == null) continue;
                        String mime = inline.optString("mimeType", "");
                        if (!mime.startsWith("audio/")) continue;
                        byte[] chunk = Base64.getDecoder().decode(inline.optString("data", ""));
                        out.write(chunk, 0, chunk.length);
                    }
                }
                e.audio = out.toByteArray();
            }
            e.turnComplete = sc.optBoolean("turnComplete", false);
            e.interrupted = sc.optBoolean("interrupted", false);
        }

        JSONObject in = msg.optJSONObject("inputTranscription");
        if (in != null) e.inputText = in.optString("text", null);
        JSONObject out = msg.optJSONObject("outputTranscription");
        if (out != null) e.outputText = out.optString("text", null);

        JSONObject resumption = msg.optJSONObject("sessionResumptionUpdate");
        if (resumption != null && resumption.optBoolean("resumable", false)) {
            String handle = resumption.optString("newHandle", "");
            if (!handle.isEmpty()) e.resumeHandle = handle;
        }

        JSONObject goAway = msg.optJSONObject("goAway");
        if (goAway != null) e.goAwayMs = parseDurationMs(goAway.optString("timeLeft", ""));

        e.toolCall = msg.has("toolCall");
        return e;
    }

    /** Protobuf-Dauer wie „58.5s“ → Millisekunden. Unlesbar → 0. */
    static long parseDurationMs(String s) {
        if (s == null || s.isEmpty()) return 0;
        String t = s.endsWith("s") ? s.substring(0, s.length() - 1) : s;
        try {
            return Math.round(Double.parseDouble(t) * 1000);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
