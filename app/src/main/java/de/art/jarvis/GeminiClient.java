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

/**
 * Google-Gemini-Anbindung (kostenloser Zugang über Google AI Studio).
 * Nimmt dieselbe Nachrichtenliste wie ClaudeClient entgegen und übersetzt sie ins Gemini-Format.
 */
final class GeminiClient {
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    void send(String apiKey, String model, String system, JSONArray messages, ClaudeClient.Callback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                body.put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", system))));
                body.put("contents", toContents(messages));
                body.put("generationConfig", new JSONObject().put("maxOutputTokens", 1024));
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

                conn = (HttpURLConnection) new URL(BASE + model + ":generateContent").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(90000);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(payload.length);
                conn.setRequestProperty("content-type", "application/json");
                conn.setRequestProperty("x-goog-api-key", apiKey);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int code = conn.getResponseCode();
                boolean ok = code >= 200 && code < 300;
                String response = readAll(ok ? conn.getInputStream() : conn.getErrorStream());
                if (!ok) {
                    final String message = describeError(code, response);
                    main.post(() -> callback.onError(message));
                    return;
                }

                StringBuilder sb = new StringBuilder();
                JSONArray candidates = new JSONObject(response).optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                    JSONArray parts = content == null ? null : content.optJSONArray("parts");
                    if (parts != null) {
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.optJSONObject(i);
                            if (part != null && !part.optBoolean("thought", false)) sb.append(part.optString("text"));
                        }
                    }
                }
                final String text = sb.toString().trim();
                main.post(() -> callback.onResult(text));
            } catch (Exception e) {
                final String message = "Ich erreiche den Server gerade nicht. " + (e.getMessage() == null ? "" : e.getMessage());
                main.post(() -> callback.onError(message));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /** Übersetzt {role, content} (Text oder Blöcke mit Bild) in Geminis {role, parts}. */
    private static JSONArray toContents(JSONArray messages) throws org.json.JSONException {
        JSONArray contents = new JSONArray();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.getJSONObject(i);
            String role = "assistant".equals(m.optString("role")) ? "model" : "user";
            JSONArray parts = new JSONArray();
            Object content = m.get("content");
            if (content instanceof JSONArray) {
                JSONArray blocks = (JSONArray) content;
                for (int j = 0; j < blocks.length(); j++) {
                    JSONObject block = blocks.getJSONObject(j);
                    if ("image".equals(block.optString("type"))) {
                        JSONObject src = block.getJSONObject("source");
                        parts.put(new JSONObject().put("inline_data", new JSONObject()
                                .put("mime_type", src.optString("media_type", "image/jpeg"))
                                .put("data", src.getString("data"))));
                    } else {
                        parts.put(new JSONObject().put("text", block.optString("text")));
                    }
                }
            } else {
                parts.put(new JSONObject().put("text", String.valueOf(content)));
            }
            contents.put(new JSONObject().put("role", role).put("parts", parts));
        }
        return contents;
    }

    private static String describeError(int code, String response) {
        String detail = "";
        try {
            JSONObject err = new JSONObject(response).optJSONObject("error");
            if (err != null) detail = err.optString("message", "");
        } catch (Exception ignored) {
            // Kein JSON – dann bleibt nur der Statuscode.
        }
        if (code == 400 && detail.toLowerCase().contains("api key")) return "Der Gemini-Schlüssel wird abgelehnt. Bitte prüfe ihn in der Jarvis-App.";
        if (code == 403) return "Gemini verweigert den Zugriff. Prüfe den Schlüssel in Google AI Studio.";
        if (code == 404) return "Das eingestellte Gemini-Modell gibt es nicht. Lass das Modellfeld in der App leer.";
        if (code == 429) return "Das kostenlose Gemini-Kontingent ist gerade ausgeschöpft. Versuch es in einer Minute noch einmal.";
        if (code >= 500) return "Die Gemini-Server sind gerade überlastet. Versuch es gleich noch einmal.";
        return "Gemini-Fehler " + code + (detail.isEmpty() ? "." : ": " + detail);
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
