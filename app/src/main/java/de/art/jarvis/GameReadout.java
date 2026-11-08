package de.art.jarvis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * „Erst lesen, dann befehlen“ (Wunsch #23: zuverlässige Bilderkennung von Gold, Stufe, Einheiten):
 * Im Spielmodus schreibt Claude zu jedem Spielbild zuerst eine stille Zeile
 * {@code [STAND: Phase 3-2 | Gold 32 | Stufe 6 | Leben 54]} und stützt den Befehl auf genau diese Werte.
 *
 * <p>Die Zeile wird nie vorgelesen ({@link SpeechChunker} lässt eckige Klammern weg), bleibt aber im
 * Gesprächsverlauf – so sieht Claude in der nächsten Runde, was er zuletzt abgelesen hat – und steht im
 * Protokoll des Gesprächstests.
 *
 * <p>Reine Java-Logik ohne Android, daher automatisch testbar.
 */
final class GameReadout {
    private static final Pattern TAG = Pattern.compile("\\[\\s*STAND\\s*:\\s*([^\\]]*?)\\s*\\]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private GameReadout() {}

    /** Die abgelesenen Werte („Phase 3-2 | Gold 32 | …“) oder null, wenn die Antwort keine Ablese-Zeile hat. */
    static String find(String answer) {
        if (answer == null) return null;
        Matcher m = TAG.matcher(answer);
        String found = null;
        while (m.find()) found = m.group(1).trim();
        return found == null || found.isEmpty() ? null : found;
    }

    /** Antwort ohne Ablese-Zeile. */
    static String strip(String answer) {
        if (answer == null) return "";
        return TAG.matcher(answer).replaceAll("").replaceAll("^[\\s]+", "").replaceAll("[ \\t]+\\n", "\n").trim();
    }

    /** Für den Gesprächsverlauf: Ablese-Zeile vor die Antwort, damit Claude sie in der nächsten Runde sieht. */
    static String forHistory(String stand, String answer) {
        return stand == null ? answer : "[STAND: " + stand + "] " + answer;
    }
}
