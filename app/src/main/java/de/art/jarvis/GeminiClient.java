package de.art.jarvis;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kostenloser KI-Weg: Google Gemini über den Gemini-API-Schlüssel aus Google AI Studio.
 *
 * Das kostenlose Kontingent gilt nur, solange für das Google-Cloud-Projekt des Schlüssels KEINE Abrechnung
 * eingerichtet ist – dann kann Google nichts berechnen, sondern antwortet bei Überschreitung mit 429.
 *
 * Format laut Gemini-Doku (generateContent, Stand 15. Nov. 2026): POST
 * {@code /v1beta/models/<modell>:streamGenerateContent?alt=sse}, Kopfzeile {@code x-goog-api-key},
 * Rollen „user“ und „model“, Grundanweisung in {@code system_instruction}. Die Antwort kommt als Server-Sent
 * Events; jede Zeile „data: {…}“ enthält ein Stück Text in candidates[0].content.parts[].text.
 *
 * Standardmodell gemini-3.5-flash-lite: im kostenlosen Kontingent, schnell, denkt standardmäßig nur minimal
 * nach (Antwortzeit zählt beim Freisprechen mehr als Tiefe).
 */
final class GeminiClient {
    static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    /** Gesprochene Antworten sind kurz; das begrenzt auch die Wartezeit. */
    static final int MAX_OUTPUT_TOKENS = 1024;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /**
     * @param history  bisheriger Verlauf im Format von JarvisService ({role: user|assistant, content: Text})
     * @param image    Bildschirmfoto als Base64-JPEG oder null
     */
    void send(String apiKey, String model, String system, List<JSONObject> history, String text, String image,
              ClaudeClient.TextListener listener, ClaudeClient.Callback callback) {
        executor.execute(() -> {
            String result = null;
            String error = null;
            HttpURLConnection conn = null;
            try {
                JSONObject body = body(system, history, text, image);
                String m = model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim();
                conn = (HttpURLConnection) new URL(BASE + m + ":streamGenerateContent?alt=sse").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(30_000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-goog-api-key", apiKey);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code != 200) {
                    error = describeError(code, readAll(conn.getErrorStream()));
                } else {
                    StringBuilder all = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            String delta = parseSseLine(line);
                            if (delta == null || delta.isEmpty()) continue;
                            all.append(delta);
                            if (listener != null) main.post(() -> listener.onText(delta));
                        }
                    }
                    result = all.toString().trim();
                    if (result.isEmpty()) error = "Gemini hat keine Antwort geliefert. Fragen Sie bitte noch einmal.";
                }
            } catch (java.net.SocketTimeoutException e) {
                error = "Gemini antwortet nicht rechtzeitig. Bitte noch einmal versuchen.";
            } catch (java.io.IOException e) {
                error = "Keine Verbindung zu Gemini. Ist das Internet an?";
            } catch (JSONException e) {
                error = "Interner Fehler beim Vorbereiten der Anfrage.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            final String ok = result;
            final String err = error;
            main.post(() -> {
                if (err != null) callback.onError(err);
                else callback.onResult(ok);
            });
        });
    }

    /** Anfrage im Gemini-Format. Reine Logik, siehe GeminiClientTest. */
    static JSONObject body(String system, List<JSONObject> history, String text, String image) throws JSONException {
        JSONArray contents = new JSONArray();
        String lastRole = null;
        for (JSONObject m : history) {
            String role = "assistant".equals(m.optString("role")) ? "model" : "user";
            String content = m.optString("content", "");
            if (content.isEmpty()) continue;
            // Gemini verlangt abwechselnde Rollen; zwei gleiche hintereinander zusammenfassen.
            if (role.equals(lastRole)) {
                JSONArray parts = contents.getJSONObject(contents.length() - 1).getJSONArray("parts");
                JSONObject p = parts.getJSONObject(parts.length() - 1);
                p.put("text", p.optString("text") + "\n" + content);
                continue;
            }
            contents.put(turn(role, new JSONArray().put(new JSONObject().put("text", content))));
            lastRole = role;
        }
        JSONArray parts = new JSONArray();
        if (image != null) {
            parts.put(new JSONObject().put("inline_data",
                    new JSONObject().put("mime_type", "image/jpeg").put("data", image)));
        }
        parts.put(new JSONObject().put("text", text));
        if ("user".equals(lastRole)) {
            // Letzte Antwort fehlt (z. B. abgebrochen) – Frage an die vorige anhängen statt Rollenfehler.
            JSONArray prev = contents.getJSONObject(contents.length() - 1).getJSONArray("parts");
            for (int i = 0; i < parts.length(); i++) prev.put(parts.get(i));
        } else {
            contents.put(turn("user", parts));
        }
        JSONObject body = new JSONObject();
        if (system != null && !system.isEmpty()) {
            body.put("system_instruction", new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", system))));
        }
        body.put("contents", contents);
        body.put("generationConfig", new JSONObject().put("maxOutputTokens", MAX_OUTPUT_TOKENS));
        return body;
    }

    private static JSONObject turn(String role, JSONArray parts) throws JSONException {
        return new JSONObject().put("role", role).put("parts", parts);
    }

    /**
     * Eine Zeile des Datenstroms → neuer Text (ohne Gedanken-Teile), "" bei Zeilen ohne Text, null bei Nicht-Daten.
     * Reine Logik, siehe GeminiClientTest.
     */
    static String parseSseLine(String line) {
        if (line == null || !line.startsWith("data:")) return null;
        String json = line.substring(5).trim();
        if (json.isEmpty() || "[DONE]".equals(json)) return "";
        try {
            JSONObject o = new JSONObject(json);
            JSONArray cands = o.optJSONArray("candidates");
            if (cands == null || cands.length() == 0) return "";
            JSONObject content = cands.getJSONObject(0).optJSONObject("content");
            if (content == null) return "";
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.getJSONObject(i);
                if (p.optBoolean("thought", false)) continue; // Nachdenken nie vorlesen
                sb.append(p.optString("text", ""));
            }
            return sb.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** Verständliche, vorlesbare Fehlermeldung. Reine Logik, siehe GeminiClientTest. */
    static String describeError(int code, String response) {
        String detail = response == null ? "" : response;
        switch (code) {
            case 400:
                if (detail.contains("API_KEY_INVALID") || detail.contains("API key not valid")) {
                    return "Der Gemini-Schlüssel ist ungültig. Bitte in den Einstellungen prüfen.";
                }
                if (detail.contains("location is not supported") || detail.contains("FAILED_PRECONDITION")) {
                    return "Gemini ist für dieses Konto oder Land so nicht freigeschaltet.";
                }
                return "Gemini hat die Anfrage abgelehnt (400).";
            case 401:
            case 403:
                return "Der Gemini-Schlüssel wird nicht akzeptiert. Bitte in den Einstellungen prüfen.";
            case 404:
                return "Das Gemini-Modell gibt es nicht mehr. Bitte das Modellfeld in den Einstellungen leeren.";
            case 429:
                return "Das kostenlose Gemini-Kontingent ist gerade aufgebraucht. Bitte in einer Minute noch einmal.";
            default:
                return code >= 500 ? "Gemini ist gerade gestört (" + code + "). Bitte gleich noch einmal."
                        : "Gemini-Fehler " + code + ".";
        }
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        try (InputStream s = in) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = s.read(buf)) > 0 && out.size() < 16_384) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (java.io.IOException e) {
            return "";
        }
    }
}
