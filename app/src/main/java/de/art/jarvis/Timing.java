package de.art.jarvis;

import java.util.Locale;

/**
 * Misst, wo bei einer Antwort die Zeit bleibt – damit wir Engpässe belegen statt raten.
 * Reines Java ohne Android-Abhängigkeiten, daher automatisch testbar.
 */
final class Timing {
    private long speechEnd;
    private long sent;
    private long answered;
    private long voiceStart;

    void reset() {
        speechEnd = sent = answered = voiceStart = 0;
    }

    /** Spracherkennung meldet: Art hat aufgehört zu sprechen (letzter Satzteil zählt). */
    void speechEnded(long now) {
        speechEnd = now;
    }

    /** Falls kein Sprechende gemeldet wurde (z. B. per Antippen abgeschickt). */
    void speechEndedIfUnknown(long now) {
        if (speechEnd == 0) speechEnd = now;
    }

    void sent(long now) {
        sent = now;
    }

    /**
     * Erstes Stück der Antwort (beim Streaming der erste fertige Satz, sonst die ganze Antwort).
     * Spätere Aufrufe ändern nichts – „KI“ misst die Wartezeit bis Jarvis etwas zu sagen hat.
     */
    void firstAnswer(long now) {
        if (answered == 0) answered = now;
    }

    /** Erster Ton der Antwort. Liefert true, wenn eine vollständige Messung vorliegt. */
    boolean voiceStarted(long now) {
        if (voiceStart != 0 || answered == 0) return false;
        voiceStart = now;
        return speechEnd != 0 && sent != 0;
    }

    /** z. B. „Zuhören 0,4 s · KI 1,8 s · Stimme 0,6 s · gesamt 2,8 s“ */
    String summary() {
        return String.format(Locale.GERMANY, "Zuhören %.1f s · KI %.1f s · Stimme %.1f s · gesamt %.1f s",
                sec(sent - speechEnd), sec(answered - sent), sec(voiceStart - answered), sec(voiceStart - speechEnd));
    }

    private static double sec(long ms) {
        return Math.max(0, ms) / 1000.0;
    }
}
