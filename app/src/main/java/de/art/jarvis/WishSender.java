package de.art.jarvis;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Legt einen Wunsch als GitHub-Issue in sroroxd/Jarvis an.
 *
 * Schnittstelle laut GitHub-Doku (geprüft 5. Nov. 2026): POST /repos/{owner}/{repo}/issues mit
 * {@code title} und {@code body}, Antwort 201 mit {@code number} und {@code html_url}. Der Schlüssel
 * ist ein „Fine-grained personal access token“ nur für dieses Repo mit „Issues: Read and write“.
 */
final class WishSender {
    interface Callback {
        /** @param issue Nummer des Issues, 0 bei Fehler; {@code error} ist dann die Meldung für Art. */
        void onDone(int issue, String error);
    }

    private static final String URL_ISSUES = "https://api.github.com/repos/sroroxd/Jarvis/issues";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    void send(String token, String title, String body, String date, Callback callback) {
        executor.execute(() -> {
            int issue = 0;
            String error = null;
            HttpURLConnection conn = null;
            try {
                byte[] payload = Wishes.issueJson(title, body, date).getBytes(StandardCharsets.UTF_8);
                conn = (HttpURLConnection) new URL(URL_ISSUES).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(payload.length);
                conn.setRequestProperty("accept", "application/vnd.github+json");
                conn.setRequestProperty("x-github-api-version", "2022-11-28");
                conn.setRequestProperty("authorization", "Bearer " + token.trim());
                conn.setRequestProperty("content-type", "application/json; charset=utf-8");
                conn.setRequestProperty("user-agent", "Jarvis-App");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
                int code = conn.getResponseCode();
                String response = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
                if (code == 201) {
                    issue = (int) AgentReply.zahl(response, "number");
                    if (issue <= 0) error = "GitHub hat die Aufgabe angelegt, aber keine Nummer zurückgegeben.";
                } else if (code == 401) {
                    error = "Der GitHub-Schlüssel ist ungültig oder abgelaufen.";
                } else if (code == 403 || code == 404) {
                    error = "Der GitHub-Schlüssel darf keine Aufgaben anlegen. Er braucht für sroroxd/Jarvis „Issues: Read and write“.";
                } else {
                    error = "GitHub hat mit Fehler " + code + " geantwortet.";
                }
            } catch (Exception e) {
                error = "GitHub ist gerade nicht erreichbar.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            final int n = issue;
            final String err = error;
            main.post(() -> callback.onDone(n, err));
        });
    }

    interface StatesCallback {
        /** Stand der Issues, oder null, wenn GitHub nicht erreichbar war. Läuft auf dem Haupt-Thread. */
        void onStates(java.util.Map<Integer, String> states);
    }

    /** Lädt den Stand aller Issues (offen, erledigt, zusammengelegt) – für die Liste in den Einstellungen. */
    void fetchStates(String token, StatesCallback callback) {
        executor.execute(() -> {
            java.util.Map<Integer, String> states = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(URL_ISSUES + "?state=all&per_page=100").openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("accept", "application/vnd.github+json");
                conn.setRequestProperty("x-github-api-version", "2022-11-28");
                // Das Repo ist öffentlich; mit Schlüssel gilt nur die höhere Abfragegrenze.
                if (token != null && !token.trim().isEmpty()) {
                    conn.setRequestProperty("authorization", "Bearer " + token.trim());
                }
                conn.setRequestProperty("user-agent", "Jarvis-App");
                if (conn.getResponseCode() == 200) states = Wishes.issueStates(readAll(conn.getInputStream()));
            } catch (Exception ignored) {
                // nicht erreichbar – Liste bleibt ohne Stand
            } finally {
                if (conn != null) conn.disconnect();
            }
            final java.util.Map<Integer, String> result = states;
            main.post(() -> callback.onStates(result));
        });
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
