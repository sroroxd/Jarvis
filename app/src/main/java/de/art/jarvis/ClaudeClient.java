package de.art.jarvis;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Schickt eine Unterhaltung an die Claude-API und liefert die Textantwort auf dem Hauptthread.
 *
 * Mit Werkzeugen („Hände“) läuft eine Schleife: Will Claude ein Werkzeug benutzen (stop_reason
 * „tool_use“), führt Jarvis es auf dem Handy aus, schickt das Ergebnis zurück und fragt erneut –
 * so lange, bis Claude normal antwortet. Die Websuche erledigt Anthropic selbst auf dem Server.
 *
 * Es gibt genau EINE Websuche-Anbindung: Sie dient den Fragen im Alltag (als Werkzeug) und der
 * täglichen TFT-Meta-Recherche ({@link #sendWithWebSearch}).
 */
final class ClaudeClient {
    interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    /** Werkzeuge, die auf dem Handy laufen (z. B. Kalender). */
    interface ToolRunner {
        /** Werkzeugbeschreibungen für die API (ohne Websuche). */
        JSONArray definitions() throws JSONException;
        /** Führt einen „tool_use“-Block aus und liefert den passenden „tool_result“-Block. */
        JSONObject run(JSONObject toolUse) throws JSONException;
    }

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    /** Höchstzahl an Runden pro Frage – schützt vor Endlosschleifen und Kosten. */
    private static final int MAX_ROUNDS = 6;
    /** Höchstens so oft „pause_turn“ fortsetzen (lange Recherchen pausiert die API zwischendurch). */
    private static final int MAX_RESUMES = 2;

    /** Fragen an Jarvis: ein gemeinsamer Thread, die Reihenfolge bleibt erhalten. */
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    /**
     * Hintergrund-Recherchen (tägliche TFT-Meta) laufen auf einem EIGENEN Thread. Vorher teilten sie sich
     * den Thread mit den Fragen – eine Frage direkt nach „Spielmodus an“ wartete dann 20–40 Sekunden,
     * bis die Recherche fertig war.
     */
    private static final ExecutorService background = Executors.newSingleThreadExecutor();
    /** Lehnt das Konto die Websuche ab (in der Claude Console nicht freigeschaltet), lassen wir sie danach weg. */
    private static volatile boolean webSearchUnavailable;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Ohne Nachdenken – schnellste Antwort. */
    void send(String apiKey, String model, String system, JSONArray messages, Callback callback) {
        send(apiKey, model, system, messages, null, null, callback);
    }

    void send(String apiKey, String model, String system, JSONArray messages, String effort, Callback callback) {
        send(apiKey, model, system, messages, effort, null, callback);
    }

    /**
     * Schickt die Anfrage. Die aktuellen Claude-Modelle denken standardmäßig nach, und dieses Nachdenken
     * zählt zu max_tokens. Ist das Budget zu knapp, kommt nur Nachdenken und KEIN Text zurück –
     * genau das führte zu den leeren „Erledigt, Sir.“-Antworten. Deshalb:
     * 0. effort == null: gar nicht nachdenken (schnellste Antwort),
     * 1. sonst großzügiges Budget und gesteuerte Nachdenk-Tiefe (effort),
     * 2. kommt trotzdem kein Text, ein zweiter Versuch ohne Nachdenken,
     * 3. kennt ein (älteres) Modell diese Parameter nicht, ein Versuch ganz ohne sie.
     *
     * tools == null: keine Werkzeuge (Spielmodus, Schlüsseltest).
     */
    void send(String apiKey, String model, String system, JSONArray messages, String effort,
              ToolRunner tools, Callback callback) {
        executor.execute(() -> {
            Result r = tools == null
                    ? ask(apiKey, model, system, messages, effort, null)
                    : converse(apiKey, model, system, messages, effort, tools);
            // Nur Fragen zählen für die Anzeige – die Meta-Recherche im Hintergrund überschrieb sonst den Wert.
            if (r.usage != null) lastUsage = r.usage;
            deliver(r, "Ich habe darauf keine Antwort bekommen. Fragen Sie mich bitte noch einmal.", callback);
        });
    }

    /**
     * Recherche mit Claudes eingebauter Websuche (z. B. die tägliche TFT-Meta). Kostet pro Suche etwa
     * 1 Cent plus Tokens; maxUses begrenzt die Zahl der Suchen. Läuft im Hintergrund, ohne Fragen zu blockieren.
     * Ergebnis ist nur der Text nach der letzten Suche – Zwischenansagen wie „Ich suche …“ fallen weg.
     */
    void sendWithWebSearch(String apiKey, String model, String system, String userText, int maxUses, Callback callback) {
        background.execute(() -> {
            Result r;
            try {
                JSONArray tools = new JSONArray().put(webSearchTool(maxUses));
                JSONArray messages = new JSONArray()
                        .put(new JSONObject().put("role", "user").put("content", userText));
                r = request(apiKey, model, system, messages, "disabled", null, 3000, tools);
                for (int i = 0; i < MAX_RESUMES && r.error == null && "pause_turn".equals(r.stopReason); i++) {
                    messages.put(new JSONObject().put("role", "assistant").put("content", r.content));
                    r = request(apiKey, model, system, messages, "disabled", null, 3000, tools);
                }
                if (r.code == 400 && mentionsWebSearch(r.detail)) {
                    r = failed("Die Websuche ist im Anthropic-Konto nicht freigeschaltet. Das lässt sich in der Claude Console ändern.");
                }
            } catch (JSONException e) {
                r = failed("Recherche fehlgeschlagen: " + e.getMessage());
            }
            deliver(r, "Die Recherche hat nichts ergeben.", callback);
        });
    }

    private void deliver(Result result, String emptyMessage, Callback callback) {
        if (result.error != null) {
            main.post(() -> callback.onError(result.error));
        } else if (result.text.isEmpty()) {
            main.post(() -> callback.onError(emptyMessage));
        } else {
            main.post(() -> callback.onResult(result.text));
        }
    }

    /** Die Werkzeug-Schleife. Arbeitet auf einer Kopie – der gespeicherte Gesprächsverlauf bleibt schlank. */
    private Result converse(String apiKey, String model, String system, JSONArray messages, String effort,
                            ToolRunner tools) {
        JSONArray convo;
        try {
            convo = new JSONArray(messages.toString());
        } catch (JSONException e) {
            return failed("Interner Fehler beim Vorbereiten der Anfrage.");
        }
        for (int round = 0; round < MAX_ROUNDS; round++) {
            JSONArray defs;
            try {
                defs = toolDefinitions(tools);
            } catch (JSONException e) {
                return failed("Interner Fehler bei den Werkzeugen.");
            }
            Result r = ask(apiKey, model, system, convo, effort, defs);
            if (r.code == 400 && mentionsWebSearch(r.detail) && !webSearchUnavailable) {
                // Websuche im Konto nicht freigeschaltet → ohne sie weitermachen statt ganz zu scheitern.
                webSearchUnavailable = true;
                round--;
                continue;
            }
            if (r.error != null) return r;
            try {
                if ("tool_use".equals(r.stopReason)) {
                    convo.put(new JSONObject().put("role", "assistant").put("content", r.content));
                    JSONArray results = new JSONArray();
                    for (JSONObject call : clientToolCalls(r.content)) results.put(tools.run(call));
                    if (results.length() == 0) return r; // nichts auszuführen – Antwort so nehmen
                    convo.put(new JSONObject().put("role", "user").put("content", results));
                    continue;
                }
                if ("pause_turn".equals(r.stopReason)) {
                    // Lange Websuche: Anthropic pausiert – Antwort unverändert zurückschicken, dann geht es weiter.
                    convo.put(new JSONObject().put("role", "assistant").put("content", r.content));
                    continue;
                }
            } catch (JSONException e) {
                return failed("Interner Fehler bei einem Werkzeug.");
            }
            return r;
        }
        return failed("Das hat zu viele Schritte gebraucht. Fragen Sie mich bitte etwas einfacher.");
    }

    /** Claudes Websuche als Server-Werkzeug (läuft bei Anthropic, nicht auf dem Handy). */
    static JSONObject webSearchTool(int maxUses) throws JSONException {
        return new JSONObject()
                .put("type", "web_search_20250305")
                .put("name", "web_search")
                .put("max_uses", maxUses) // Kostenbremse
                .put("user_location", new JSONObject()
                        .put("type", "approximate")
                        .put("country", "DE")
                        .put("timezone", "Europe/Berlin"));
    }

    private static JSONArray toolDefinitions(ToolRunner tools) throws JSONException {
        JSONArray defs = new JSONArray();
        if (!webSearchUnavailable) defs.put(webSearchTool(3)); // höchstens drei Suchen pro Frage
        JSONArray own = tools.definitions();
        for (int i = 0; i < own.length(); i++) defs.put(own.get(i));
        return defs;
    }

    /** Eine Anfrage samt der Rückfallversuche rund ums Nachdenken. */
    private Result ask(String apiKey, String model, String system, JSONArray messages, String effort, JSONArray tools) {
        Result r = effort == null
                ? request(apiKey, model, system, messages, "disabled", null, 1024, tools)
                : request(apiKey, model, system, messages, "adaptive", effort, 4096, tools);
        boolean wantsMore = "tool_use".equals(r.stopReason) || "pause_turn".equals(r.stopReason);
        if (r.error == null && r.text.isEmpty() && effort != null && !wantsMore) {
            r = request(apiKey, model, system, messages, "disabled", null, 1024, tools);
        } else if (r.code == 400 && mentionsThinking(r.detail)) {
            r = request(apiKey, model, system, messages, null, null, 1024, tools);
        }
        return r;
    }

    private static boolean mentionsThinking(String detail) {
        String d = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        return d.contains("thinking") || d.contains("effort") || d.contains("output_config");
    }

    static boolean mentionsWebSearch(String detail) {
        String d = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        return d.contains("web_search") || d.contains("web search");
    }

    private static Result failed(String message) {
        Result r = new Result();
        r.error = message;
        return r;
    }

    private static final class Result {
        int code;
        String text = "";
        String error;
        String detail = "";
        String stopReason = "";
        JSONArray content = new JSONArray();
        Usage usage;
    }

    /**
     * Der Text, der vorgelesen wird: nur die Textblöcke NACH dem letzten Werkzeugblock.
     * So wird ein „Ich suche kurz danach …“ vor der Websuche nicht mit vorgelesen.
     * Nachdenkblöcke („thinking“) werden übersprungen.
     */
    static String spokenText(JSONArray content) {
        if (content == null) return "";
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            String type = block == null ? "" : block.optString("type");
            if (!type.equals("text") && !type.equals("thinking") && !type.equals("redacted_thinking")) start = i + 1;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block != null && "text".equals(block.optString("type"))) sb.append(block.optString("text"));
        }
        if (sb.length() == 0 && start > 0) {
            // Kein Text nach dem Werkzeug – dann lieber alles, was an Text da ist.
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                if (block != null && "text".equals(block.optString("type"))) sb.append(block.optString("text"));
            }
        }
        return sb.toString().trim();
    }

    /** Werkzeugaufrufe, die das Handy ausführen muss (die Websuche läuft dagegen auf dem Server). */
    static List<JSONObject> clientToolCalls(JSONArray content) {
        List<JSONObject> calls = new ArrayList<>();
        if (content == null) return calls;
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block != null && "tool_use".equals(block.optString("type"))) calls.add(block);
        }
        return calls;
    }

    /**
     * Der Anfrage-Körper. Die Grundanweisung (system) geht als Textblock mit Cache-Marke raus: Werkzeuge und
     * Grundanweisung bleiben von Frage zu Frage gleich, Claude liest sie dann aus dem Zwischenspeicher – bei
     * Sonnet 5.5 für 5 % des normalen Eingabepreises statt 100 %. Die Marke sitzt bewusst am Ende der
     * Grundanweisung und nicht am Ende des Verlaufs: Der Verlauf wird ohne Bildschirmfotos gespeichert, das
     * Ende jeder Anfrage ist also nie wiederverwendbar – dort zu cachen, kostete nur den Aufschlag fürs Schreiben.
     * Unter 512 Tokens cacht die API nicht, liefert dann aber auch keinen Fehler.
     */
    static JSONObject body(String model, int maxTokens, String thinking, String effort, String system,
                           JSONArray messages, JSONArray tools) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", maxTokens); // Nachdenken + Antwort teilen sich dieses Budget
        if (thinking != null) body.put("thinking", new JSONObject().put("type", thinking));
        if (effort != null) body.put("output_config", new JSONObject().put("effort", effort));
        // Kein „temperature“: Die aktuellen Claude-Modelle lehnen den Parameter ab (Fehler 400).
        if (system != null && !system.trim().isEmpty()) {
            // Leere Textblöcke lehnt die API ab – ohne Grundanweisung fällt das Feld ganz weg.
            body.put("system", new JSONArray().put(new JSONObject()
                    .put("type", "text")
                    .put("text", system)
                    .put("cache_control", new JSONObject().put("type", "ephemeral"))));
        }
        body.put("messages", messages);
        if (tools != null && tools.length() > 0) body.put("tools", tools);
        return body;
    }

    /** Verbrauch der letzten Anfrage (für die Anzeige auf dem Startbildschirm), null = noch keine. */
    static volatile Usage lastUsage;

    /** Eingabe-Tokens einer Antwort, aufgeteilt in normal, aus dem Zwischenspeicher gelesen und neu hineingeschrieben. */
    static final class Usage {
        final int input;
        final int cacheRead;
        final int cacheWrite;

        Usage(int input, int cacheRead, int cacheWrite) {
            this.input = input;
            this.cacheRead = cacheRead;
            this.cacheWrite = cacheWrite;
        }

        static Usage from(JSONObject usage) {
            if (usage == null) return null;
            return new Usage(usage.optInt("input_tokens", 0),
                    usage.optInt("cache_read_input_tokens", 0),
                    usage.optInt("cache_creation_input_tokens", 0));
        }

        /** Kurz für die Anzeige, z. B. „Cache 2,4 Tsd. gelesen“. */
        String summary() {
            if (cacheRead > 0) return "Cache " + thousands(cacheRead) + " gelesen";
            if (cacheWrite > 0) return "Cache " + thousands(cacheWrite) + " neu";
            return "ohne Cache";
        }

        private static String thousands(int tokens) {
            return String.format(java.util.Locale.GERMANY, "%.1f Tsd.", tokens / 1000.0);
        }
    }

    /** thinking: "adaptive", "disabled" oder null (Feld weglassen); effort: "low"/"medium"/… oder null. */
    private Result request(String apiKey, String model, String system, JSONArray messages,
                           String thinking, String effort, int maxTokens, JSONArray tools) {
        Result result = new Result();
        HttpURLConnection conn;
        try {
            JSONObject body = body(model, maxTokens, thinking, effort, system, messages, tools);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

            conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(payload.length);
            conn.setRequestProperty("content-type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int code = conn.getResponseCode();
            result.code = code;
            boolean ok = code >= 200 && code < 300;
            String response = readAll(ok ? conn.getInputStream() : conn.getErrorStream());
            if (!ok) {
                result.error = describeError(code, response);
                try {
                    JSONObject err = new JSONObject(response).optJSONObject("error");
                    if (err != null) result.detail = err.optString("message", "");
                } catch (Exception ignored) {
                    // kein JSON
                }
                return result;
            }

            JSONObject json = new JSONObject(response);
            result.usage = Usage.from(json.optJSONObject("usage"));
            result.stopReason = json.optString("stop_reason", "");
            JSONArray content = json.optJSONArray("content");
            if (content != null) result.content = content;
            result.text = spokenText(content);
        } catch (Exception e) {
            result.error = "Ich erreiche den Server gerade nicht. " + (e.getMessage() == null ? "" : e.getMessage());
        }
        // Kein disconnect(): So bleibt die Verbindung offen und wird für die nächste Frage
        // wiederverwendet – das spart jedes Mal den Verbindungsaufbau (TLS-Handshake).
        return result;
    }

    private static String describeError(int code, String response) {
        String type = "";
        String detail = "";
        try {
            JSONObject err = new JSONObject(response).optJSONObject("error");
            if (err != null) {
                type = err.optString("type", "");
                detail = err.optString("message", "");
            }
        } catch (Exception ignored) {
            // Kein JSON – dann bleibt nur der Statuscode.
        }
        return Text.claudeError(code, type, detail);
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
