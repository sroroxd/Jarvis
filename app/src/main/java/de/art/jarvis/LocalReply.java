package de.art.jarvis;

/**
 * Offline-Testantwort: Jarvis antwortet ohne KI, ohne Netz und ohne Kosten.
 *
 * Zweck ist der Gerätetest der Sprachkette auf dem S24 Ultra, nicht Intelligenz:
 * - Die Antwort ist absichtlich mehrere Sätze lang, damit Art mittendrin „Stopp“ sagen kann.
 * - Sie wiederholt die verstandene Frage – so hört Art, ob die Spracherkennung richtig lag.
 * - Bei einer Folgefrage nennt sie die vorige Frage: Beweis, dass der Gesprächsverlauf ankommt,
 *   ohne dass der Gesprächsmodus neu gestartet wurde.
 *
 * Reine Java-Logik, siehe LocalReplyTest.
 */
final class LocalReply {
    private LocalReply() { }

    /** Erkennungszeichen im Testprotokoll und im Gerätetest (geraetetest.sh sucht danach). */
    static final String MARK = "Offline-Test";
    static final String FOLLOW_MARK = "Ihre vorige Frage war";

    /**
     * @param question  was Art gerade gesagt hat
     * @param previous  Arts vorige Frage im selben Gespräch, oder null bei der ersten Frage
     * @param turn      die wievielte Frage im Gespräch (1 = erste)
     */
    static String answer(String question, String previous, int turn) {
        String q = clean(question);
        StringBuilder sb = new StringBuilder();
        if (previous == null || clean(previous).isEmpty()) {
            sb.append(MARK).append(", Frage ").append(turn).append(". Ich habe verstanden: ").append(q).append(". ");
        } else {
            sb.append(MARK).append(", Folgefrage ").append(turn).append(". ")
              .append(FOLLOW_MARK).append(": ").append(clean(previous)).append(". ")
              .append("Jetzt fragen Sie: ").append(q).append(". ");
        }
        sb.append("Ich bin im Offline-Testmodus und habe keine KI angeschlossen, deshalb kann ich die Frage nicht "
                + "wirklich beantworten. ");
        sb.append("Sie können mich jetzt mit Stopp unterbrechen. ");
        sb.append("Wenn Sie nichts sagen, rede ich noch ein paar Sätze weiter, damit genug Zeit zum Unterbrechen bleibt. ");
        sb.append("Für echte Antworten tragen Sie in den Einstellungen einen kostenlosen Gemini-Schlüssel ein. ");
        sb.append("Danach können Sie einfach die nächste Frage stellen, ich höre weiter zu.");
        return sb.toString();
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", " ");
        while (t.endsWith(".") || t.endsWith("?") || t.endsWith("!")) t = t.substring(0, t.length() - 1).trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}
