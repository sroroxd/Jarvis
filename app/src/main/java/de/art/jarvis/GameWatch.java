package de.art.jarvis;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

/**
 * Schaut alle drei Sekunden nach, welche App vorne ist ({@link UsageStatsManager#queryEvents}), und meldet,
 * wenn TFT nach vorne kommt oder verlassen wurde. Die Entscheidung trifft {@link GameDetector}.
 *
 * <p>Braucht die Freigabe „Nutzungsdatenzugriff“ (PACKAGE_USAGE_STATS), die Art einmalig in den
 * Android-Einstellungen erteilt. Läuft nur bei eingeschaltetem Bildschirm (der Dienst startet und stoppt sie).
 * Alle Methoden laufen auf dem Haupt-Thread.
 */
final class GameWatch {
    interface Listener {
        void onGameChanged(boolean inGame);
    }

    private static final long POLL_MS = 3000;
    /** Beim Start so weit zurückschauen, damit ein schon laufendes TFT erkannt wird. */
    private static final long FIRST_LOOKBACK_MS = 30 * 60 * 1000L;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GameDetector detector = new GameDetector();
    private long queriedUntil;
    private boolean running;

    private final Runnable poll = this::poll;

    GameWatch(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** Hat Art den Nutzungsdatenzugriff erteilt? */
    static boolean permitted(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    boolean inGame() {
        return detector.inGame();
    }

    void start() {
        if (running) return;
        running = true;
        if (queriedUntil == 0) queriedUntil = System.currentTimeMillis() - FIRST_LOOKBACK_MS;
        main.post(poll);
    }

    void stop() {
        running = false;
        main.removeCallbacks(poll);
    }

    private void poll() {
        if (!running) return;
        long now = System.currentTimeMillis();
        try {
            UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
            UsageEvents events = usm == null ? null : usm.queryEvents(queriedUntil, now);
            if (events != null) { // null, solange das Handy gesperrt ist
                UsageEvents.Event e = new UsageEvents.Event();
                while (events.hasNextEvent()) {
                    events.getNextEvent(e);
                    detector.event(e.getTimeStamp(), e.getEventType(), e.getPackageName());
                }
                queriedUntil = now;
            }
            GameDetector.Change change = detector.update(now);
            if (change == GameDetector.Change.ENTERED) listener.onGameChanged(true);
            else if (change == GameDetector.Change.LEFT) listener.onGameChanged(false);
        } catch (RuntimeException ignored) {
            // Freigabe entzogen o. Ä. – beim nächsten Mal wieder versuchen
        }
        if (running) main.postDelayed(poll, POLL_MS);
    }
}
