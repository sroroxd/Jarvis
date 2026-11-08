package de.art.jarvis;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regeln für „Immer zuhören“ (Gesamtanalyse vom 8. Nov., Punkt 1): wann das Zuhören neu anläuft, wie oft
 * es bei Fehlern der Spracherkennung wiederholt wird, und wann eine gehörte Äußerung erst durch den günstigen
 * Filter muss, bevor sie an Sonnet geht.
 *
 * <p>Anlass:
 * <ul>
 *   <li>Nach fünf Fehlern der Spracherkennung gab Jarvis das Zuhören auf und nichts warf es wieder an – er war
 *       stumm, obwohl „Immer zuhören“ an war, bis Art den Bildschirm aus- und einschaltete.</li>
 *   <li>Jede erkannte Äußerung ging an Sonnet, auch Fernseher und Gespräche mit anderen (0,1–0,2 ct, mit
 *       Bildschirmfoto 1,3 ct). Der Filter (Haiku, nur Text) kostet rund 0,0015 ct.</li>
 * </ul>
 *
 * <p>Reines Java ohne Android, daher automatisch testbar.
 */
final class ListenRules {
    private ListenRules() {}

    /** So oft wird nach einem echten Fehler schnell (0,5–8 s) wiederholt. */
    static final int QUICK_RETRIES = 5;
    /** Danach mit „Immer zuhören“: in diesem Abstand weiter versuchen, statt aufzugeben. */
    static final long SLOW_RETRY_MS = 30_000;
    /** So oft schaut der Wächter nach, ob Jarvis zuhören sollte, es aber nicht tut. */
    static final long WATCHDOG_MS = 20_000;
    /**
     * Innerhalb dieser Zeit nach Jarvis' letzter Antwort (oder nach Antippen) gilt eine Äußerung als Teil des
     * Gesprächs und geht ohne Filter an Claude – Folgefragen bleiben so schnell wie bisher. Eine Minute deckt im
     * Spiel die Zeit zwischen zwei Ansagen ab.
     */
    static final long WARM_MS = 60_000;

    /** Wartezeit bis zum nächsten Versuch nach dem soundsovielten Fehler in Folge; -1 = aufgeben. */
    static long retryDelay(int errors, boolean alwaysListen) {
        if (errors <= QUICK_RETRIES) return Math.min(8000, 500L << Math.max(0, errors));
        return alwaysListen ? SLOW_RETRY_MS : -1;
    }

    /**
     * Soll der Wächter das Zuhören anwerfen? Nur wenn Jarvis zuhören soll, gerade nichts tut (kein Zuhören,
     * Denken, Sprechen) und auch kein Neustart des Zuhörens schon ansteht.
     */
    static boolean kickNeeded(boolean wanted, boolean continuous, boolean idle, boolean relistenPending) {
        if (!wanted || !idle) return false;
        return !continuous || !relistenPending;
    }

    private static final Pattern JARVIS = Pattern.compile("\\bjarvis\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Muss die Äußerung erst durch den Filter? Nur bei offenem Mikrofon („Immer zuhören“), nur nach einer
     * Gesprächspause, und nicht, wenn Art Jarvis beim Namen nennt.
     */
    static boolean needsGate(boolean openMic, long now, long lastExchange, String utterance) {
        if (!openMic) return false;
        if (now - lastExchange < WARM_MS) return false;
        return utterance == null || !JARVIS.matcher(utterance).find();
    }

    static final String GATE_SYSTEM = "Du entscheidest, ob ein gehörter Satz an den Sprachassistenten Jarvis gerichtet "
            + "ist. Antworte mit genau einem Wort: JA oder NEIN.";

    /** Frage an den Filter: was gehört wurde, was Jarvis zuletzt sagte, und in welcher Lage. */
    static String gatePrompt(String heard, String lastJarvis, long secondsSince, boolean game) {
        StringBuilder sb = new StringBuilder();
        sb.append("Jarvis hört über das Handy-Mikrofon durchgehend zu. Art spricht ihn oft ohne seinen Namen an. ");
        sb.append(game ? "Art spielt gerade Teamfight Tactics; Jarvis ist sein Coach. "
                : "Art benutzt das Handy im Alltag. ");
        if (lastJarvis != null && !lastJarvis.trim().isEmpty()) {
            sb.append("Jarvis sagte zuletzt (vor ").append(secondsSince).append(" Sekunden): „")
                    .append(shorten(lastJarvis.trim(), 200)).append("“\n");
        }
        sb.append("Gehört: „").append(heard == null ? "" : heard.trim()).append("“\n");
        sb.append("Ist das eine Frage, Bitte oder Antwort an Jarvis? NEIN bei Gesprächen mit anderen Menschen, "
                + "Fernseher, Video, Musik, Selbstgesprächen oder sinnlosen Bruchstücken. Im Zweifel JA.");
        return sb.toString();
    }

    /** Antwort des Filters: Nur ein klares NEIN hält zurück – im Zweifel antwortet Jarvis. */
    static boolean addressed(String reply) {
        if (reply == null) return true;
        String t = reply.trim().toUpperCase(Locale.GERMANY).replaceFirst("^[^A-ZÄÖÜ]+", "");
        return !t.startsWith("NEIN");
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + " …";
    }
}
