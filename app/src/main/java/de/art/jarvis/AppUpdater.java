package de.art.jarvis;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Spielt eine neue Version direkt ein – ohne Browser und ohne Datei im Download-Ordner.
 *
 * Die APK wird von GitHub gestreamt und sofort in eine Installationssitzung des Systems geschrieben;
 * auf dem Handy entsteht dabei keine Datei, die liegen bleibt. Ab Android 12 darf sich eine App laut
 * Android-Doku ohne Rückfrage selbst aktualisieren, wenn sie {@code UPDATE_PACKAGES_WITHOUT_USER_ACTION}
 * erklärt, die Erlaubnis „Apps installieren“ hat und hoch genug zielt (bei uns targetSdk 35). Verlangt
 * Android trotzdem eine Bestätigung, öffnet {@link UpdateReceiver} den Systemdialog.
 */
final class AppUpdater {
    interface Progress {
        /** Kurzer Statustext für die Anzeige (läuft auf dem Haupt-Thread). */
        void status(String text);

        /** Endgültig fehlgeschlagen – die Anzeige soll einen neuen Versuch anbieten (Haupt-Thread). */
        default void failed(String text) {
            status(text);
        }
    }

    /** Versuche insgesamt, abwechselnd über die Adressen aus {@link UpdateCheck#downloadUrls}. */
    private static final int ATTEMPTS = 4;

    static final String ACTION_RESULT = "de.art.jarvis.UPDATE_RESULT";

    private AppUpdater() {}

    /**
     * @return false, wenn zuerst die Erlaubnis „Apps installieren“ fehlt – dann ist deren
     *         Einstellungsseite geöffnet und Art tippt danach noch einmal auf „Installieren“.
     */
    static boolean install(Activity activity, String apiUrl, String apkUrl, int build, Progress progress) {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            return false;
        }
        Activity app = activity;
        new Thread(() -> {
            String error = runWithRetries(app, UpdateCheck.downloadUrls(apiUrl, apkUrl), build, progress);
            if (error != null) app.runOnUiThread(() -> progress.failed(error));
        }, "Jarvis-Update").start();
        return true;
    }

    /** Ergebnis eines Versuchs: Fehlertext (null = übergeben) und ob ein neuer Versuch lohnt. */
    private static final class Attempt {
        final String error;
        final boolean retry;

        Attempt(String error, boolean retry) {
            this.error = error;
            this.retry = retry;
        }
    }

    /** @return Fehlermeldung oder null, wenn die Installation übergeben wurde. */
    private static String runWithRetries(Activity activity, String[] urls, int build, Progress progress) {
        String last = null;
        for (int i = 0; i < ATTEMPTS; i++) {
            if (i > 0) {
                final String why = last;
                activity.runOnUiThread(() -> progress.status(why + " – neuer Versuch …"));
                try {
                    Thread.sleep(2000L * i); // 2, 4, 6 s
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return last;
                }
            }
            Attempt a = run(activity, urls[i % urls.length], build, progress);
            if (a.error == null) return null;
            last = a.error;
            if (!a.retry) return last;
        }
        return last + ". Bitte gleich noch einmal versuchen.";
    }

    private static Attempt run(Activity activity, String apkUrl, int build, Progress progress) {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int sessionId = -1;
        boolean committed = false;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apkUrl).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true); // GitHub leitet auf seinen Dateispeicher weiter
            conn.setRequestProperty("user-agent", "Jarvis-App");
            // API-Adresse: liefert mit diesem Accept die Datei selbst (Weiterleitung zum Dateispeicher) statt JSON.
            if (UpdateCheck.isApiUrl(apkUrl)) conn.setRequestProperty("accept", "application/octet-stream");
            int code = conn.getResponseCode();
            if (code != 200) {
                return new Attempt(code >= 500 ? "GitHub antwortet gerade nicht (" + code + ")"
                        : "Download fehlgeschlagen (" + code + ")", UpdateCheck.retryable(code));
            }
            long total = conn.getContentLengthLong();

            sessionId = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                // Reihenfolge laut Android-Doku: schreiben, fsync, Stream EINMAL schließen, dann commit.
                // Vorher schloss try-with-resources den Stream nach dem commit ein zweites Mal. Der Stream
                // des Systems verträgt das nicht („write failed: EBADF“), und der Fehlerzweig unten verwarf
                // daraufhin die schon übergebene Installation („INSTALL_FAILED_ABORTED“, Arts Test am 8. Nov.).
                try (InputStream in = conn.getInputStream();
                     OutputStream out = session.openWrite("jarvis.apk", 0, total)) {
                    byte[] buf = new byte[64 * 1024];
                    long done = 0;
                    int lastPercent = -1;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        done += n;
                        int percent = total > 0 ? (int) (done * 100 / total) : -1;
                        if (percent >= 0 && percent / 10 != lastPercent / 10) {
                            lastPercent = percent;
                            final int p = percent;
                            activity.runOnUiThread(() -> progress.status("Build " + build + " wird geladen: " + p + " %"));
                        }
                    }
                    session.fsync(out);
                } // hier wird der Stream genau einmal geschlossen
                Intent result = new Intent(activity, UpdateReceiver.class).setAction(ACTION_RESULT);
                // MUTABLE: Android trägt Status und ggf. den Bestätigungsdialog als Extras ein.
                PendingIntent pi = PendingIntent.getBroadcast(activity, sessionId, result,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                activity.runOnUiThread(() -> progress.status("Build " + build + " wird installiert …"));
                session.commit(pi.getIntentSender());
                committed = true;
            }
            return new Attempt(null, false);
        } catch (Exception e) {
            // Eine übergebene Installation nie verwerfen – Android meldet ihr Ergebnis an UpdateReceiver.
            if (committed) return new Attempt(null, false);
            if (sessionId != -1) {
                try {
                    installer.abandonSession(sessionId);
                } catch (Exception ignored) {
                    // schon weg
                }
            }
            // Netz weg, Zeitüberschreitung, abgerissener Download: neuer Versuch mit neuer Sitzung.
            return new Attempt("Update fehlgeschlagen: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()),
                    e instanceof java.io.IOException);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
