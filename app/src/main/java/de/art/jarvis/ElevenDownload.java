package de.art.jarvis;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Ein abbrechbarer Sprach-Download, ohne Android und ohne eigenen Thread.
 *
 * <p>Nur die Verbindung dieses Auftrags wird abgebrochen. Überholte Aufträge in der gemeinsamen
 * Warteschlange öffnen keine Verbindung mehr. Jede Antwort bekommt ihre eigene Datei: Ein alter
 * Download kann dadurch niemals die gerade abgespielte MP3 überschreiben.
 */
final class ElevenDownload {
    interface ConnectionFactory {
        /** Erzeugt und konfiguriert die Verbindung, ohne sie bereits zu öffnen. */
        HttpURLConnection create() throws IOException;
    }

    interface Listener {
        /** Übernimmt die Datei und muss sie nach der Wiedergabe oder beim Verwerfen löschen. */
        void onAudio(File file);
        void onHttpError(int code, String body);
        void onFailure();
    }

    private volatile boolean cancelled;
    private boolean started;
    private Future<?> task;
    private HttpURLConnection connection;

    synchronized void start(ExecutorService executor, ConnectionFactory factory, byte[] payload,
                            File cacheDir, Listener listener) {
        if (started) throw new IllegalStateException("Sprach-Download wurde bereits gestartet");
        started = true;
        if (cancelled) return;
        task = executor.submit(() -> download(factory, payload, cacheDir, listener));
    }

    boolean cancelled() {
        return cancelled;
    }

    /** Unterbricht auch blockierendes Netz-Lesen, statt erst dessen Timeout abzuwarten. */
    void cancel() {
        Future<?> oldTask;
        HttpURLConnection oldConnection;
        synchronized (this) {
            cancelled = true;
            oldTask = task;
            oldConnection = connection;
            connection = null;
        }
        if (oldTask != null) oldTask.cancel(true);
        disconnect(oldConnection);
    }

    private synchronized boolean attach(HttpURLConnection candidate) {
        if (cancelled) return false;
        connection = candidate;
        return true;
    }

    private synchronized void detach(HttpURLConnection candidate) {
        if (connection == candidate) connection = null;
    }

    private void requireActive() throws InterruptedIOException {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Sprach-Download abgebrochen");
        }
    }

    private void download(ConnectionFactory factory, byte[] payload, File cacheDir, Listener listener) {
        HttpURLConnection conn = null;
        File file = null;
        boolean delivered = false;
        boolean consumed = false;
        try {
            requireActive();
            conn = factory.create();
            if (!attach(conn)) return;
            requireActive();
            try (OutputStream out = conn.getOutputStream()) {
                requireActive();
                out.write(payload);
            }
            requireActive();
            int code = conn.getResponseCode();
            requireActive();
            if (code < 200 || code >= 300) {
                String body = readError(conn.getErrorStream());
                consumed = true;
                requireActive();
                listener.onHttpError(code, body);
                return;
            }
            file = File.createTempFile("jarvis_stimme_", ".mp3", cacheDir);
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[8192];
                while (true) {
                    requireActive();
                    int n = in.read(buf);
                    requireActive();
                    if (n == -1) break;
                    out.write(buf, 0, n);
                }
            }
            consumed = true;
            requireActive();
            listener.onAudio(file);
            delivered = true;
        } catch (Exception e) {
            if (!cancelled) listener.onFailure();
        } finally {
            detach(conn);
            // Vollständig gelesene Antworten dürfen die HTTP-Verbindung wiederverwenden.
            if (!consumed || cancelled) disconnect(conn);
            if (!delivered && file != null) file.delete();
        }
    }

    private String readError(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            while (true) {
                requireActive();
                int n = in.read(buf);
                requireActive();
                if (n == -1) return out.toString(StandardCharsets.UTF_8.name());
                out.write(buf, 0, n);
            }
        }
    }

    private static void disconnect(HttpURLConnection conn) {
        if (conn == null) return;
        try {
            conn.disconnect();
        } catch (RuntimeException ignored) {
            // Die andere Seite kann die Verbindung gleichzeitig schon geschlossen haben.
        }
    }
}
