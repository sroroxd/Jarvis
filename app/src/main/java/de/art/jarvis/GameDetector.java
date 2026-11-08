package de.art.jarvis;

/**
 * Erkennt, ob TFT gerade im Vordergrund läuft – aus den Nutzungsereignissen des Systems
 * ({@code UsageEvents.Event.ACTIVITY_RESUMED}: eine App kommt nach vorne).
 *
 * <p>Rein: TFT kommt nach vorne → sofort {@link Change#ENTERED}. Raus: erst, wenn eine andere App
 * {@link #LEAVE_AFTER_MS} lang vorne war – ein kurzer Blick auf Discord oder die Jarvis-App beendet
 * den Spielmodus nicht.
 *
 * <p>Reine Java-Logik ohne Android, daher automatisch testbar. Das Abfragen macht {@link GameWatch}.
 */
final class GameDetector {
    /** Paket von „TFT: Teamfight Tactics“ im Play Store; regionale Ableger hängen etwas an. */
    static final String TFT_PACKAGE = "com.riotgames.league.teamfighttactics";
    /** {@code UsageEvents.Event.ACTIVITY_RESUMED} (Wert laut Android-Doku). */
    static final int ACTIVITY_RESUMED = 1;
    static final long LEAVE_AFTER_MS = 20_000;

    enum Change { NONE, ENTERED, LEFT }

    private String foreground = "";
    private long foregroundSince;
    private boolean inGame;

    static boolean isTft(String pkg) {
        return pkg != null && pkg.startsWith(TFT_PACKAGE);
    }

    /** Ein Ereignis aus {@code UsageStatsManager.queryEvents}. Nur „nach vorne gekommen“ zählt. */
    void event(long time, int type, String pkg) {
        if (type != ACTIVITY_RESUMED || pkg == null || time < foregroundSince) return;
        if (!pkg.equals(foreground)) {
            foreground = pkg;
            foregroundSince = time;
        }
    }

    /** Nach jeder Abfrage aufrufen. Meldet den Wechsel ins Spiel oder (nach der Gnadenfrist) heraus. */
    Change update(long now) {
        boolean tft = isTft(foreground);
        if (tft && !inGame) {
            inGame = true;
            return Change.ENTERED;
        }
        if (!tft && inGame && now - foregroundSince >= LEAVE_AFTER_MS) {
            inGame = false;
            return Change.LEFT;
        }
        return Change.NONE;
    }

    boolean inGame() {
        return inGame;
    }

    String foreground() {
        return foreground;
    }
}
