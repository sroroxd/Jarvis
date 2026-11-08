package de.art.jarvis;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

    /**
     * Empfängt die Antwort Stück für Stück, während Claude noch schreibt (Streaming). Läuft auf dem Haupt-Thread,
     * in derselben Reihenfolge wie die Stücke kamen; {@link Callback#onResult} kommt danach mit dem ganzen Text.
     */
    interface TextListener {
        void onText(String delta);
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
    /** Kurze Nebenanfragen (Phasen-Wächter des proaktiven Coachings) – eigener Thread, hält weder Fragen noch Meta auf. */
    private static final ExecutorService quick = Executors.newSingleThreadExecutor();
    /** Lehnt das Konto die Websuche ab (in der Claude Console nicht freigeschaltet), lassen wir sie danach weg. */
    private static volatile boolean webSearchUnavailable;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Knappste Antwort (z. B. Schlüsseltest). */
    void send(String apiKey, String model, String system, JSONArray messages, Callback callback) {
        send(apiKey, model, system, messages, null, null, null, callback);
    }

    void send(String apiKey, String model, String system, JSONArray messages, String effort, Callback callback) {
        send(apiKey, model, system, messages, effort, null, null, callback);
    }

    void send(String apiKey, String model, String system, JSONArray messages, String effort,
              ToolRunner tools, Callback callback) {
        send(apiKey, model, system, messages, effort, tools, null, callback);
    }

    /**
     * Schickt die Anfrage. Die aktuellen Claude-Modelle denken standardmäßig nach, und dieses Nachdenken
     * zählt zu max_tokens. Ist das Budget zu knapp, kommt nur Nachdenken und KEIN Text zurück –
     * genau das führte zu den leeren „Erledigt, Sir.“-Antworten. Deshalb:
     * 1. adaptives Nachdenken mit gesteuerter Tiefe (effort; null = „low“) und großzügigem Budget,
     * 2. kommt trotzdem kein Text, ein zweiter Versuch mit „low“ und doppeltem Budget,
     * 3. kennt ein (älteres) Modell diese Parameter nicht, ein Versuch ganz ohne sie.
     *
     * Nie „thinking: disabled“: Sonnet 5.5 (der Standard) lehnt das mit Fehler 400 ab, Opus 5.5 ebenso.
     * Laut Claude-Doku ist „low“ mit adaptivem Nachdenken der empfohlene schnelle Weg – bei einfachen
     * Fragen denkt das Modell dann gar nicht nach.
     *
     * tools == null: keine Werkzeuge (Spielmodus, Schlüsseltest). listener != null: Antwort wird gestreamt.
     */
    void send(String apiKey, String model, String system, JSONArray messages, String effort,
              ToolRunner tools, TextListener listener, Callback callback) {
        executor.execute(() -> {
            answering.set(true);
            spentHere.get()[0] = 0;
            Result r = tools == null
                    ? ask(apiKey, model, system, messages, effort, null, listener)
                    : converse(apiKey, model, system, messages, effort, tools, listener);
            // Nur Fragen zählen für die Anzeige – die Meta-Recherche im Hintergrund überschrieb sonst den Wert.
            if (r.usage != null) lastUsage = r.usage;
            lastCostUsd = Costs.known(model) ? spentHere.get()[0] : -1;
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
                // Kein „thinking: disabled“ (siehe send): adaptiv mit „low“, Budget für Nachdenken und Text.
                r = request(apiKey, model, system, messages, "adaptive", "low", 4096, tools, null);
                for (int i = 0; i < MAX_RESUMES && r.error == null && "pause_turn".equals(r.stopReason); i++) {
                    messages.put(new JSONObject().put("role", "assistant").put("content", r.content));
                    r = request(apiKey, model, system, messages, "adaptive", "low", 4096, tools, null);
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

    /**
     * Kurze Nebenanfrage ohne Werkzeuge und ohne Streaming (z. B. der Phasen-Wächter: „PLANUNG 3-2“ oder „KAMPF“).
     * Adaptiv mit „low“ – bei so einfachen Fragen denkt das Modell laut Doku meist gar nicht nach.
     */
    void sendQuick(String apiKey, String model, String system, JSONArray messages, Callback callback) {
        quick.execute(() -> {
            Result r = request(apiKey, model, system, messages, "adaptive", "low", 1024, null, null);
            if (r.code == 400 && mentionsThinking(r.detail)) r = request(apiKey, model, system, messages, null, null, 256, null, null);
            deliver(r, "Keine Antwort.", callback);
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
                            ToolRunner tools, TextListener listener) {
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
            Result r = ask(apiKey, model, system, convo, effort, defs, listener);
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
    private Result ask(String apiKey, String model, String system, JSONArray messages, String effort, JSONArray tools,
                       TextListener listener) {
        Result r = request(apiKey, model, system, messages, "adaptive", effort == null ? "low" : effort, 4096, tools, listener);
        boolean wantsMore = "tool_use".equals(r.stopReason) || "pause_turn".equals(r.stopReason);
        if (r.error == null && r.text.isEmpty() && !wantsMore && !"refusal".equals(r.stopReason)) {
            // Nur Nachdenken, kein Text (Budget aufgebraucht): knapper nachdenken, doppelt so viel Platz.
            // Vorher folgte hier „thinking: disabled“ – das lehnt Sonnet 5.5 mit 400 ab, der Rückfall scheiterte also immer.
            r = request(apiKey, model, system, messages, "adaptive", "low", 8192, tools, listener);
        } else if (r.code == 400 && mentionsThinking(r.detail)) {
            // Älteres Modell ohne adaptives Nachdenken oder effort: ganz ohne diese Felder.
            r = request(apiKey, model, system, messages, null, null, 2048, tools, listener);
        }
        if (r.error == null && r.text.isEmpty() && "refusal".equals(r.stopReason)) {
            r.error = "Dazu kann ich leider nichts sagen, Sir.";
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

    /** Kosten der letzten Frage in US-Dollar – alle Teilanfragen zusammen (Rückfall, Werkzeuge); -1 = unbekannt. */
    static volatile double lastCostUsd = -1;

    /** Bekommt jede abgerechnete Anfrage, auch Phasen-Wächter und Meta-Recherche (für die Tagessumme). Beliebiger Thread. */
    interface CostListener {
        void spent(double usd);
    }

    static volatile CostListener costListener;
    /** Summe der Anfragen dieses Threads seit dem letzten Zurücksetzen – jeder Executor hat seinen eigenen Thread. */
    private static final ThreadLocal<double[]> spentHere = ThreadLocal.withInitial(() -> new double[1]);

    private static void account(String model, Usage u) {
        if (u == null || !Costs.known(model)) return;
        double usd = Costs.usd(model, u.input, u.cacheWrite, u.cacheRead, u.output, u.searches);
        spentHere.get()[0] += usd;
        CostListener l = costListener;
        if (l != null) l.spent(usd);
    }

    /** Verbrauch einer Antwort: Eingabe aufgeteilt in normal, aus dem Zwischenspeicher gelesen und neu hineingeschrieben. */
    static final class Usage {
        final int input;
        final int cacheRead;
        final int cacheWrite;
        final int output;
        /** Websuchen dieser Anfrage (je 1 Cent). */
        final int searches;

        Usage(int input, int cacheRead, int cacheWrite) {
            this(input, cacheRead, cacheWrite, 0, 0);
        }

        Usage(int input, int cacheRead, int cacheWrite, int output, int searches) {
            this.input = input;
            this.cacheRead = cacheRead;
            this.cacheWrite = cacheWrite;
            this.output = output;
            this.searches = searches;
        }

        static Usage from(JSONObject usage) {
            if (usage == null) return null;
            JSONObject server = usage.optJSONObject("server_tool_use");
            return new Usage(usage.optInt("input_tokens", 0),
                    usage.optInt("cache_read_input_tokens", 0),
                    usage.optInt("cache_creation_input_tokens", 0),
                    usage.optInt("output_tokens", 0),
                    server == null ? 0 : server.optInt("web_search_requests", 0));
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

    /**
     * Wo die Wartezeit der letzten Anfrage steckt (Wunsch #43): Senden = Verbindung plus Hochladen der Anfrage
     * (mit Bildschirmfoto mehrere hundert KB), Claude = vom fertigen Hochladen bis zum ersten Textstück.
     * Was danach bis zum ersten gesprochenen Satz fehlt, ist der Rest des Satzes und der Start der Stimme.
     */
    static final class Net {
        final long start;
        final int bytes;
        volatile long uploaded;
        volatile long firstText;

        Net(long start, int bytes) {
            this.start = start;
            this.bytes = bytes;
        }

        /** z. B. „Senden 0,4 s (380 KB) · Claude 0,9 s“; null, solange noch kein Text kam. */
        String summary() {
            if (uploaded == 0 || firstText == 0) return null;
            return String.format(java.util.Locale.GERMANY, "Senden %.1f s (%d KB) · Claude %.1f s",
                    Math.max(0, uploaded - start) / 1000.0, Math.round(bytes / 1024.0),
                    Math.max(0, firstText - uploaded) / 1000.0);
        }

        static long now() {
            return System.nanoTime() / 1_000_000L;
        }
    }

    /**
     * Zeitpunkte der letzten Antwort auf eine Frage – für die Zeitmessung auf dem Startbildschirm und den Gerätetest.
     * Nur Anfragen aus {@link #send} zählen: Vorher überschrieben Phasen-Wächter (alle 6 s) und Meta-Recherche den
     * Wert, und die Zeile zeigte deren Zeiten statt der von Arts Frage.
     */
    static volatile Net lastNet;
    /** Läuft dieser Thread Arts Fragen ({@link #send})? Nur dann wird {@link #lastNet} gesetzt. */
    private static final ThreadLocal<Boolean> answering = ThreadLocal.withInitial(() -> false);

    /**
     * thinking: "adaptive" oder null (Feld weglassen); effort: "low"/"medium"/… oder null.
     * listener != null: als Strom anfordern und Textstücke sofort weiterreichen.
     */
    private Result request(String apiKey, String model, String system, JSONArray messages,
                           String thinking, String effort, int maxTokens, JSONArray tools, TextListener listener) {
        Result result = new Result();
        HttpURLConnection conn;
        try {
            JSONObject body = body(model, maxTokens, thinking, effort, system, messages, tools);
            if (listener != null) body.put("stream", true);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            Net net = new Net(Net.now(), payload.length);
            if (answering.get()) lastNet = net;

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
            net.uploaded = Net.now();

            int code = conn.getResponseCode();
            result.code = code;
            boolean ok = code >= 200 && code < 300;
            if (ok && listener != null) {
                readStream(conn.getInputStream(), delta -> {
                    if (net.firstText == 0) net.firstText = Net.now();
                    listener.onText(delta);
                }, result);
                account(model, result.usage);
                return result;
            }
            String response = Io.readAll(ok ? conn.getInputStream() : conn.getErrorStream());
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

            if (net.firstText == 0) net.firstText = Net.now(); // ohne Strom: ganze Antwort auf einmal
            JSONObject json = new JSONObject(response);
            result.usage = Usage.from(json.optJSONObject("usage"));
            account(model, result.usage);
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

    /**
     * Liest eine gestreamte Antwort (Server-Sent Events) bis zum Ende. Textstücke gehen sofort an den Haupt-Thread;
     * am Ende steht in {@code result} dasselbe wie bei einer Antwort ohne Streaming.
     */
    private void readStream(InputStream in, TextListener listener, Result result) throws java.io.IOException, JSONException {
        StreamAccumulator acc = new StreamAccumulator(delta -> main.post(() -> listener.onText(delta)));
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) acc.line(line);
        }
        result.content = acc.content();
        result.stopReason = acc.stopReason();
        result.usage = Usage.from(acc.usage());
        result.text = spokenText(result.content);
        if (acc.errorType() != null) {
            result.code = "overloaded_error".equals(acc.errorType()) ? 529 : 500;
            result.detail = acc.errorMessage();
            result.error = Text.claudeError(result.code, acc.errorType(), acc.errorMessage());
        } else if (!acc.complete()) {
            result.error = "Die Verbindung ist mitten in der Antwort abgerissen. Fragen Sie mich bitte noch einmal.";
        }
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

}
