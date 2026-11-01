package de.art.jarvis;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Schickt eine Unterhaltung an die Claude-API und liefert die Textantwort auf dem Hauptthread. */
final class ClaudeClient {
    interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";

    /** Ein gemeinsamer Hintergrund-Thread für alle Anfragen (vorher: pro Client ein neuer, der nie endete). */
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Alltag: wenig Nachdenken (schnell). */
    void send(String apiKey, String model, String system, JSONArray messages, Callback callback) {
        send(apiKey, model, system, messages, "low", callback);
    }

    /**
     * Schickt die Anfrage. Die aktuellen Claude-Modelle denken standardmäßig nach, und dieses Nachdenken
     * zählt zu max_tokens. Ist das Budget zu knapp, kommt nur Nachdenken und KEIN Text zurück –
     * genau das führte zu den leeren „Erledigt, Sir.“-Antworten. Deshalb:
     * 1. großzügiges Budget und gesteuerte Nachdenk-Tiefe (effort),
     * 2. kommt trotzdem kein Text, ein zweiter Versuch ohne Nachdenken,
     * 3. kennt ein (älteres) Modell diese Parameter nicht, ein Versuch ganz ohne sie.
     */
    void send(String apiKey, String model, String system, JSONArray messages, String effort, Callback callback) {
        executor.execute(() -> {
            Result r = request(apiKey, model, system, messages, "adaptive", effort, 4096);
            if (r.error == null && r.text.isEmpty()) {
                r = request(apiKey, model, system, messages, "disabled", null, 1024);
            } else if (r.code == 400 && mentionsThinking(r.detail)) {
                r = request(apiKey, model, system, messages, null, null, 1024);
            }
            final Result result = r;
            if (result.error != null) {
                main.post(() -> callback.onError(result.error));
            } else if (result.text.isEmpty()) {
                main.post(() -> callback.onError("Ich habe darauf keine Antwort bekommen. Frag mich bitte noch einmal."));
            } else {
                main.post(() -> callback.onResult(result.text));
            }
        });
    }

    private static boolean mentionsThinking(String detail) {
        String d = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        return d.contains("thinking") || d.contains("effort") || d.contains("output_config");
    }

    private static final class Result {
        int code;
        String text = "";
        String error;
        String detail = "";
    }

    /** thinking: "adaptive", "disabled" oder null (Feld weglassen); effort: "low"/"medium"/… oder null. */
    private Result request(String apiKey, String model, String system, JSONArray messages,
                           String thinking, String effort, int maxTokens) {
        Result result = new Result();
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("max_tokens", maxTokens); // Nachdenken + Antwort teilen sich dieses Budget
            if (thinking != null) body.put("thinking", new JSONObject().put("type", thinking));
            if (effort != null) body.put("output_config", new JSONObject().put("effort", effort));
            // Kein „temperature“: Die aktuellen Claude-Modelle lehnen den Parameter ab (Fehler 400).
            body.put("system", system);
            body.put("messages", messages);
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

            // Nur Textblöcke zählen – Nachdenkblöcke („thinking“) werden übersprungen.
            JSONArray content = new JSONObject(response).optJSONArray("content");
            StringBuilder sb = new StringBuilder();
            if (content != null) {
                for (int i = 0; i < content.length(); i++) {
                    JSONObject block = content.optJSONObject(i);
                    if (block != null && "text".equals(block.optString("type"))) {
                        sb.append(block.optString("text"));
                    }
                }
            }
            result.text = sb.toString().trim();
        } catch (Exception e) {
            result.error = "Ich erreiche den Server gerade nicht. " + (e.getMessage() == null ? "" : e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
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
