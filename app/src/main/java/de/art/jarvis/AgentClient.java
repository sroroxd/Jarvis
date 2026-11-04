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
 * Brücke zum Jarvis-Agenten, der in Termux auf demselben Handy läuft
 * (siehe {@code agent/} im Repo).
 *
 * Der Agent macht die langen Aufgaben – Dateien, Recherche, Zeitpläne – über
 * Claude Code und damit über Arts Abo statt über das API-Guthaben. Die schnellen
 * Wege (Stimme, Spielmodus) laufen weiter über {@link ClaudeClient}: der Agent
 * braucht Sekunden bis Minuten und wäre fürs Sprechen zu träge.
 *
 * Verbindung geht ausschließlich auf 127.0.0.1 und trägt das Token des Agenten.
 */
final class AgentClient {
    interface Callback {
        void onResult(AgentReply reply);
    }

    /** Eigener Thread: ein Auftrag kann Minuten dauern und darf die Anfragen an Claude nicht blockieren. */
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** So lange wartet die App auf das Ergebnis eines Auftrags. */
    private static final int AUFTRAG_TIMEOUT_MS = 10 * 60 * 1000;
    /** Kurzes Zeitlimit für die Verbindungsprüfung. */
    private static final int STATUS_TIMEOUT_MS = 2500;

    static String basis(int port) {
        return "http://127.0.0.1:" + port;
    }

    /** Schickt einen Auftrag. Das Ergebnis kommt auf dem Hauptthread zurück. */
    void auftrag(int port, String token, String text, boolean neu, Callback callback) {
        executor.execute(() -> {
            AgentReply r = postAuftrag(port, token, text, neu);
            main.post(() -> callback.onResult(r));
        });
    }

    /** Setzt die Sitzung des Agenten zurück, damit das nächste Gespräch frisch anfängt. */
    void neueSitzung(int port, String token) {
        executor.execute(() -> anfrage(port, token, "/neu", "{}", STATUS_TIMEOUT_MS));
    }

    /** Prüft, ob der Agent läuft. Antwort: {@code null} = läuft, sonst die Meldung. */
    void pruefen(int port, String token, Callback callback) {
        executor.execute(() -> {
            Antwort a = anfrage(port, token, "/status", null, STATUS_TIMEOUT_MS);
            AgentReply r;
            if (a.code == 200 && a.body.contains("\"bereit\"")) {
                boolean beschaeftigt = a.body.contains("\"beschaeftigt\":true");
                r = AgentReply.parse(200, "{\"antwort\":\""
                        + (beschaeftigt ? "Der Agent läuft und arbeitet gerade." : "Der Agent läuft und ist bereit.")
                        + "\"}");
            } else if (a.code == 0) {
                r = AgentReply.fehler("Der Agent antwortet nicht. Starte ihn in Termux mit „jarvis-agent“.");
            } else {
                r = AgentReply.parse(a.code, a.body);
            }
            main.post(() -> callback.onResult(r));
        });
    }

    private AgentReply postAuftrag(int port, String token, String text, boolean neu) {
        if (token == null || token.trim().isEmpty()) {
            return AgentReply.fehler("Mir fehlt das Token des Agenten. Es steht in Termux in ~/.jarvis-agent/token.");
        }
        String body = "{\"text\":" + AgentReply.jsonText(text) + ",\"neu\":" + (neu ? "true" : "false") + "}";
        Antwort a = anfrage(port, token, "/auftrag", body, AUFTRAG_TIMEOUT_MS);
        if (a.code == 0) {
            return AgentReply.fehler("Ich erreiche den Agenten nicht. Läuft er in Termux? Starte ihn mit „jarvis-agent“.");
        }
        return AgentReply.parse(a.code, a.body);
    }

    private static final class Antwort {
        int code;
        String body = "";
    }

    private Antwort anfrage(int port, String token, String pfad, String body, int timeout) {
        Antwort ergebnis = new Antwort();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(basis(port) + pfad).openConnection();
            conn.setRequestMethod(body == null ? "GET" : "POST");
            conn.setConnectTimeout(STATUS_TIMEOUT_MS);
            conn.setReadTimeout(timeout);
            if (token != null && !token.trim().isEmpty()) {
                conn.setRequestProperty("authorization", "Bearer " + token.trim());
            }
            if (body != null) {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(payload.length);
                conn.setRequestProperty("content-type", "application/json; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }
            int code = conn.getResponseCode();
            ergebnis.code = code;
            boolean ok = code >= 200 && code < 300;
            ergebnis.body = readAll(ok ? conn.getInputStream() : conn.getErrorStream());
        } catch (Exception e) {
            ergebnis.code = 0;
            ergebnis.body = e.getMessage() == null ? "" : e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return ergebnis;
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
