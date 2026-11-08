package de.art.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proaktives Coaching (Wunsch #41/#42): Jarvis meldet sich zu Beginn jeder Planungsphase von selbst mit dem
 * wichtigsten Befehl samt Aufstellung – ohne dass Art fragen muss.
 *
 * <p>Zwei Stufen, damit es gut UND bezahlbar ist: Ein günstiger Phasen-Wächter (Claude Haiku auf einem
 * verkleinerten Bild, alle paar Sekunden) meldet nur „PLANUNG 3-2“, „KAMPF“ oder „ANDERES“. Erst wenn danach eine
 * neue Planungsphase beginnt, coacht Sonnet mit dem scharfen Bild – einmal pro Runde.
 *
 * <p>Reine Java-Logik ohne Android, daher automatisch testbar.
 */
final class ProactiveCoach {
    enum Kind { PLANNING, COMBAT, OTHER }

    /** Antwort des Phasen-Wächters. */
    static final class Phase {
        final Kind kind;
        /** Spielphase wie „3-2“, oder null, wenn nicht lesbar. */
        final String stage;

        Phase(Kind kind, String stage) {
            this.kind = kind;
            this.stage = stage;
        }
    }

    /** Frühestens so viel Abstand zwischen zwei Ansagen – eine Planungsphase dauert rund eine halbe Minute. */
    static final long MIN_GAP_MS = 25_000;
    /** So oft schaut der Phasen-Wächter hin. */
    static final long CHECK_EVERY_MS = 6_000;

    /** Grundanweisung des Phasen-Wächters (kurz, damit er schnell und günstig ist). */
    static final String WATCH_SYSTEM = "Du erkennst Spielphasen in Teamfight Tactics auf einem Handy-Bildschirmfoto. "
            + "Antworte mit genau einer Zeile und nichts sonst.";
    static final String WATCH_PROMPT = "Was zeigt dieses Bildschirmfoto? Antworte genau so:\n"
            + "PLANUNG <Phase> – Teamfight Tactics in der Planungsphase (Einheiten kaufen und aufstellen, der Kampf hat noch "
            + "nicht begonnen); <Phase> wie oben in der Mitte angezeigt, zum Beispiel 3-2, sonst ?\n"
            + "KAMPF – die Einheiten kämpfen gerade\n"
            + "ANDERES – Karussell, Ladebildschirm, Menü, Lobby oder gar nicht TFT";
    /** Was die App Claude statt einer Frage schickt, wenn eine neue Planungsphase beginnt. */
    static final String AUTO_PREFIX = "[AUTO]";

    private static final Pattern STAGE = Pattern.compile("(\\d)\\s*[-–‑]\\s*(\\d)");
    private static final Pattern STILL = Pattern.compile("\\[\\s*STILL\\s*\\]", Pattern.CASE_INSENSITIVE);

    private String lastStage;
    private long lastCoach = Long.MIN_VALUE / 2;
    /** Seit der letzten Ansage etwas anderes als Planung gesehen (Kampf, Karussell)? */
    private boolean sawOther = true;
    /** Zuletzt vom Wächter gemeldete Phase – damit eine eigene Frage von Art die Ansage dieser Runde ersetzt. */
    private Phase lastSeen;
    private long lastSeenAt = Long.MIN_VALUE / 2;

    static Phase parse(String reply) {
        String t = reply == null ? "" : reply.trim().toUpperCase(Locale.GERMANY);
        if (t.startsWith("PLANUNG")) {
            Matcher m = STAGE.matcher(t);
            return new Phase(Kind.PLANNING, m.find() ? m.group(1) + "-" + m.group(2) : null);
        }
        if (t.startsWith("KAMPF")) return new Phase(Kind.COMBAT, null);
        return new Phase(Kind.OTHER, null);
    }

    /**
     * Ist jetzt eine Ansage fällig? Ja, wenn Planung läuft, der Abstand reicht und es eine NEUE Planungsphase ist:
     * dazwischen lag ein Kampf (oder Karussell) – oder die gelesene Phase hat sich geändert, falls der Wächter den
     * Kampf verpasst hat.
     */
    boolean due(Phase p, long now) {
        lastSeen = p;
        lastSeenAt = now;
        if (p.kind != Kind.PLANNING) {
            sawOther = true;
            return false;
        }
        if (now - lastCoach < MIN_GAP_MS) return false;
        boolean newStage = p.stage != null && lastStage != null && !p.stage.equals(lastStage);
        return sawOther || newStage;
    }

    /** Ansage ist raus. */
    void coached(Phase p, long now) {
        if (p.stage != null) lastStage = p.stage;
        lastCoach = now;
        sawOther = false;
    }

    /**
     * Art hat selbst gefragt und eine Antwort bekommen. Lief laut Wächter gerade eine Planungsphase, ist diese Runde
     * damit erledigt – Jarvis wiederholt danach nicht ungefragt dasselbe.
     */
    void userAsked(long now) {
        if (lastSeen != null && lastSeen.kind == Kind.PLANNING && now - lastSeenAt <= 2 * CHECK_EVERY_MS) {
            coached(lastSeen, now);
        }
    }

    /** Was die App Claude schickt. */
    static String autoMessage(Phase p) {
        return AUTO_PREFIX + " Neue Planungsphase" + (p.stage == null ? "" : " " + p.stage) + ".";
    }

    /** Hat Claude entschieden zu schweigen ([STILL] und sonst nichts)? */
    static boolean isStill(String answer) {
        if (answer == null || !STILL.matcher(answer).find()) return false;
        return STILL.matcher(answer).replaceAll("").trim().isEmpty();
    }
}
