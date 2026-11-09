package de.art.jarvis;

/**
 * Zwischenansagen für lange Agenten-Aufträge: Statt minutenlang zu schweigen, sagt Jarvis
 * gelegentlich, woran der Agent gerade arbeitet (der Agent liefert den Stand über
 * {@code GET /fortschritt}, siehe {@code agent/jarvis-agent.mjs}).
 *
 * <p>Reines Java ohne Android – daher in den automatischen Tests geprüft.
 */
final class AgentProgress {
    private AgentProgress() {}

    /** Mindestabstand zwischen zwei Zwischenansagen. Auch der Vorlauf bis zur ersten Ansage. */
    static final long ABSTAND_MS = 60_000;

    /** Ist eine Zwischenansage fällig? {@code letzteAnsage} ist die letzte Ansage oder der Auftragsstart. */
    static boolean faellig(long jetzt, long letzteAnsage) {
        return jetzt - letzteAnsage >= ABSTAND_MS;
    }

    /**
     * Gesprochene Zwischenansage zum aktuellen Stand. Leerer Stand → allgemeine Beruhigung.
     * Der Stand kommt vom Agenten schon in Ich-Form oder als kurzer Satz.
     */
    static String ansage(String fortschritt) {
        String f = fortschritt == null ? "" : fortschritt.trim();
        if (f.isEmpty()) return "Ich bin noch dran, Sir.";
        return "Moment noch, Sir – " + mitSatzende(f);
    }

    private static String mitSatzende(String f) {
        char c = f.charAt(f.length() - 1);
        if (c == '.' || c == '!' || c == '?' || c == '…') return f;
        return f + ".";
    }
}
