package de.art.jarvis;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Kurze Sprachbefehle, die die App selbst erledigt – sofort, zuverlässig und ohne KI-Kosten.
 * Reiner Text ohne Android-Abhängigkeiten, daher automatisch testbar.
 */
final class Commands {
    private Commands() {}

    private static final Pattern GAME = Pattern.compile(
            "\\b(spiel(e)?[- ]?modus|tft[- ]?modus|gaming[- ]?modus|game[- ]?modus|spielemodus|spielmodus)\\b");
    private static final Pattern EVERYDAY = Pattern.compile("\\b(alltags?[- ]?modus|normal(en|er)?[- ]?modus)\\b");
    private static final Pattern ON = Pattern.compile(
            "\\b(an|ein|einschalten|anschalten|aktivieren|aktiviere|aktiviert|starten|starte|start|los)\\b");
    private static final Pattern OFF = Pattern.compile(
            "\\b(aus|ausschalten|abschalten|deaktivieren|deaktiviere|deaktiviert|beenden|beende|stopp|stop|verlassen)\\b");

    /** „Neue Partie“, „neues Spiel“, „nächste Partie“ … – bewusst NICHT „neue Runde“ (Runden gibt es in jeder Partie). */
    private static final Pattern NEW_GAME = Pattern.compile(
            "^(ok(ay)?\\s+)?(also\\s+)?(eine\\s+)?(neue|neues|nächste|nächstes)\\s+(partie|spiel|match|game)"
                    + "(\\s+(beginnt|fängt an|geht los|startet|starten|anfangen|los))?(\\s+(bitte|jarvis|sir))*$");

    /**
     * Erkennt „Neue Partie“ (und Varianten): Jarvis vergisst dann den Verlauf der letzten Partie,
     * damit alte Pläne („Danach: …“) nicht in die neue Partie hineinwirken.
     */
    static boolean newGame(String utterance) {
        String t = normalize(utterance);
        if (t.isEmpty() || t.split(" ").length > 6) return false;
        return NEW_GAME.matcher(t).matches();
    }

    private static String normalize(String utterance) {
        if (utterance == null) return "";
        return utterance.toLowerCase(Locale.GERMANY)
                .replaceAll("[^a-zäöüß0-9 -]", " ")
                .replaceAll("^\\s*((hey|okay|ok)\\s+)?(jarvis\\s+)?(bitte\\s+)?", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Erkennt „Spielmodus an/aus“ (und Varianten). Liefert {@code TRUE} = einschalten,
     * {@code FALSE} = ausschalten, {@code null} = kein solcher Befehl.
     * Nur kurze Sätze zählen, damit Fragen wie „Was bringt der Spielmodus an Vorteilen?“ nicht auslösen.
     */
    static Boolean gameMode(String utterance) {
        if (utterance == null) return null;
        String t = normalize(utterance);
        if (t.isEmpty() || t.split(" ").length > 6) return null;
        if (EVERYDAY.matcher(t).find()) {
            if (OFF.matcher(t).find()) return Boolean.TRUE; // „Alltagsmodus aus“ = Spielmodus an
            return Boolean.FALSE;
        }
        if (!GAME.matcher(t).find()) return null;
        boolean off = OFF.matcher(t).find();
        boolean on = ON.matcher(t).find();
        if (off == on) return null; // unklar
        return on;
    }

    /**
     * Auslöser für das Agenten-Rückgrat: Sätze, mit denen Art eine längere
     * Aufgabe abgibt. Der Agent (Claude Code in Termux) braucht Sekunden bis
     * Minuten – deshalb geht nur dorthin, was Art ausdrücklich abgibt, und nicht
     * jede Frage.
     *
     * Erkannt werden „Übernimm …“, „Auftrag: …“, „Kümmere dich um …“,
     * „Erledige …“, „Arbeite daran, …“ und „Agent: …“.
     *
     * UNICODE_CASE ist nötig: ohne das greift CASE_INSENSITIVE nur bei ASCII,
     * und „Übernimm“ mit großem Ü wäre kein Treffer.
     */
    private static final Pattern AGENT_KOPF = Pattern.compile(
            "^(?:"
                    + "agent(?:\\s+jarvis)?|neuer\\s+auftrag|auftrag"
                    + "|(?:bitte\\s+)?(?:übernimm|uebernimm)"
                    + "|(?:bitte\\s+)?(?:kümmere?|kuemmere?)\\s+dich(?:\\s+um|\\s+darum)?"
                    + "|(?:bitte\\s+)?erledige"
                    + "|(?:bitte\\s+)?arbeite\\s+(?:daran|dran)"
                    + ")"
                    // Der Auslöser muss als ganzes Wort enden: „Agenten gibt es viele“ ist kein Auftrag.
                    + "(?=[\\s:,.!?]|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Füllwörter, die allein keinen Auftrag ergeben („Übernimm das.“). */
    private static final Pattern FUELLWORT_KETTE = Pattern.compile(
            "^(?:(?:das|dies|du|es|mal|bitte|darum|drum|dich|um)\\b[\\s]*)+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Liefert den Auftragstext, wenn die Äußerung eine Aufgabe an den Agenten
     * abgibt – sonst {@code null}. Groß- und Kleinschreibung bleibt erhalten,
     * damit Namen und Dateinamen unverändert beim Agenten ankommen.
     */
    static String agentTask(String utterance) {
        if (utterance == null) return null;
        String t = utterance.trim()
                .replaceAll("(?i)^\\s*(?:(?:hey|okay|ok)\\s+)?(?:jarvis\\s*[,:]?\\s*)?", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (t.isEmpty()) return null;
        java.util.regex.Matcher kopf = AGENT_KOPF.matcher(t);
        if (!kopf.find()) return null;

        String rest = t.substring(kopf.end()).replaceAll("^[\\s:,.!?]+", "").trim();
        // „Übernimm das: X“ – die Füllwortkette vor dem Doppelpunkt gehört zum Auslöser.
        java.util.regex.Matcher fuell = FUELLWORT_KETTE.matcher(rest);
        if (fuell.find()) {
            String dahinter = rest.substring(fuell.end()).trim();
            if (dahinter.startsWith(":") || dahinter.startsWith(",")) {
                rest = dahinter.substring(1).trim();
            } else if (dahinter.replaceAll("[\\s.!?]+", "").isEmpty()) {
                return null; // nur Füllwörter, kein Auftrag
            }
        }
        if (rest.replaceAll("[\\s.!?]+", "").length() < 3) return null;
        return rest;
    }
}
