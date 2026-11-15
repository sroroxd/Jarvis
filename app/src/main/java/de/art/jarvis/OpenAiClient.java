package de.art.jarvis;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** OpenAI Responses API, independent of Claude credentials and endpoints. */
final class OpenAiClient {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    void send(String key, String model, String system, JSONArray messages, ClaudeClient.Callback cb) {
        WORK.execute(() -> {
            try {
                if (key == null || key.isEmpty()) throw new IllegalArgumentException("OpenAI-API-Schlüssel fehlt.");
                JSONObject body = new JSONObject().put("model", model)
                        .put("instructions", system).put("store", false)
                        .put("max_output_tokens", 1200).put("input", toInput(messages));
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                HttpURLConnection conn = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(90000);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(data.length);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + key);
                try (OutputStream os = conn.getOutputStream()) { os.write(data); }
                int status = conn.getResponseCode();
                String response = Io.readAll(status < 400 ? conn.getInputStream() : conn.getErrorStream());
                JSONObject json = new JSONObject(response);
                if (status >= 400) {
                    JSONObject err = json.optJSONObject("error");
                    String detail = err == null ? "" : err.optString("message", "");
                    String msg = "OpenAI HTTP " + status + ": " + detail;
                    ui.post(() -> cb.onError(msg));
                    return;
                }
                String text = outputText(json.optJSONArray("output"));
                if (text.isEmpty()) ui.post(() -> cb.onError("OpenAI lieferte keinen Antworttext."));
                else ui.post(() -> cb.onResult(text));
            } catch (Exception e) {
                String error = "OpenAI-Verbindung: " + e.getMessage();
                ui.post(() -> cb.onError(error));
            }
        });
    }

    static JSONArray toInput(JSONArray old) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject message = old.getJSONObject(i);
            String role = message.optString("role");
            if (!"user".equals(role) && !"assistant".equals(role)) continue;
            JSONArray content = new JSONArray();
            Object value = message.opt("content");
            if (value instanceof String) {
                content.put(new JSONObject().put("type", role.equals("assistant") ? "output_text" : "input_text")
                        .put("text", value));
            } else if (value instanceof JSONArray) {
                JSONArray parts = (JSONArray) value;
                for (int j = 0; j < parts.length(); j++) {
                    JSONObject part = parts.optJSONObject(j);
                    if (part == null) continue;
                    if ("text".equals(part.optString("type"))) {
                        content.put(new JSONObject().put("type", role.equals("assistant") ? "output_text" : "input_text")
                                .put("text", part.optString("text")));
                    } else if ("user".equals(role) && "image".equals(part.optString("type"))) {
                        JSONObject src = part.optJSONObject("source");
                        if (src != null && "base64".equals(src.optString("type"))) {
                            content.put(new JSONObject().put("type", "input_image")
                                    .put("image_url", "data:" + src.optString("media_type", "image/jpeg")
                                            + ";base64," + src.optString("data")));
                        }
                    }
                }
            }
            if (content.length() > 0)
                result.put(new JSONObject().put("role", role).put("content", content));
        }
        return result;
    }

    static String outputText(JSONArray output) {
        StringBuilder result = new StringBuilder();
        if (output == null) return "";
        for (int i = 0; i < output.length(); i++) {
            JSONObject message = output.optJSONObject(i);
            if (message == null || !"message".equals(message.optString("type"))) continue;
            JSONArray parts = message.optJSONArray("content");
            if (parts == null) continue;
            for (int j = 0; j < parts.length(); j++) {
                JSONObject part = parts.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) {
                    if (result.length() > 0) result.append("\n");
                    result.append(part.optString("text"));
                }
            }
        }
        return result.toString().trim();
    }
}
