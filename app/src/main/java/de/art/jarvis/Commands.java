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

    /**
     * Erkennt „Spielmodus an/aus“ (und Varianten). Liefert {@code TRUE} = einschalten,
     * {@code FALSE} = ausschalten, {@code null} = kein solcher Befehl.
     * Nur kurze Sätze zählen, damit Fragen wie „Was bringt der Spielmodus an Vorteilen?“ nicht auslösen.
     */
    static Boolean gameMode(String utterance) {
        if (utterance == null) return null;
        String t = utterance.toLowerCase(Locale.GERMANY)
                .replaceAll("[^a-zäöüß0-9 -]", " ")
                .replaceAll("^\\s*((hey|okay|ok)\\s+)?(jarvis\\s+)?(bitte\\s+)?", "")
                .replaceAll("\\s+", " ")
                .trim();
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
}
