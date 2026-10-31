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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    void send(String apiKey, String model, String system, JSONArray messages, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("max_tokens", 1024);
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
                boolean ok = code >= 200 && code < 300;
                String response = readAll(ok ? conn.getInputStream() : conn.getErrorStream());
                if (!ok) {
                    final String message = describeError(code, response);
                    main.post(() -> callback.onError(message));
                    return;
                }

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

    private static String describeError(int code, String response) {
        String detail = "";
        try {
            JSONObject err = new JSONObject(response).optJSONObject("error");
            if (err != null) detail = err.optString("message", "");
        } catch (Exception ignored) {
            // Kein JSON – dann bleibt nur der Statuscode.
        }
        String lower = detail.toLowerCase();
        if (code == 401) return "Der API-Schlüssel wird abgelehnt. Bitte prüfe ihn in der Jarvis-App.";
        if (lower.contains("credit") || lower.contains("balance")) return "Das API-Guthaben ist aufgebraucht. Bitte in der Anthropic-Konsole aufladen.";
        if (code == 404 || lower.contains("model")) return "Das eingestellte Modell ist nicht verfügbar. Fehler " + code + ": " + detail;
        if (code == 429) return "Zu viele Anfragen in kurzer Zeit. Versuch es gleich noch einmal.";
        if (code == 529 || code >= 500) return "Die Claude-Server sind gerade überlastet. Versuch es gleich noch einmal.";
        return "Fehler " + code + (detail.isEmpty() ? "." : ": " + detail);
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
