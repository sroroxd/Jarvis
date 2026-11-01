package de.art.jarvis;

/**
 * Reine Text-Hilfen ohne Android-Abhängigkeiten – dadurch automatisch testbar.
 */
final class Text {
    private Text() {}

    /** Entfernt alles, was beim Vorlesen stört: Markdown, Aufzählungszeichen, Nummerierungen, Emojis. */
    static String cleanForSpeech(String s) {
        if (s == null) return "";
        String t = s.replaceAll("(?m)^\\s*(?:[-*•–]|\\d+[.)])\\s+", "");
        t = t.replaceAll("[*#_`>~|]", "");
        t = t.replaceAll("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}]", "");
        t = t.replaceAll("([^.!?:;,\\s])\\s*\\n+\\s*", "$1. ");
        t = t.replaceAll("\\s*\\n+\\s*", " ");
        return t.replaceAll(" {2,}", " ").trim();
    }

    /** Entfernt Leerzeichen, Zeilenumbrüche und unsichtbare Zeichen, die beim Kopieren mitkommen. */
    static String cleanKey(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\s\\u00A0\\u200B-\\u200D\\u2060\\uFEFF]", "");
    }

    /** Nur echte Modellnamen zählen („claude-…“ oder „gemini-…“) – Tippfehler wie „2“ werden ignoriert. */
    static boolean isValidModel(String m) {
        return m != null && m.matches("(?i)(claude|gemini)-[a-z0-9.\\-]+");
    }

    static boolean isGeminiKey(String key) {
        return key != null && key.startsWith("AIza");
    }

    /** Gekürzte Anzeige, z. B. „sk-ant-api…x7Qa (108 Zeichen, Claude)“ – zum Abgleich mit der Konsole. */
    static String keyPreview(String k) {
        if (k == null || k.isEmpty()) return "Kein Schlüssel gespeichert.";
        String head = k.substring(0, Math.min(10, k.length()));
        String tail = k.length() > 14 ? k.substring(k.length() - 4) : "";
        String kind = isGeminiKey(k) ? "Gemini" : k.startsWith("sk-ant-") ? "Claude" : "unbekanntes Format";
        return "Gespeichert: " + head + "…" + tail + " (" + k.length() + " Zeichen, " + kind + ")";
    }

    /** Verständliche Meldung zu einem Claude-API-Fehler (Typ und Text kommen aus der Fehlerantwort). */
    static String claudeError(int code, String type, String detail) {
        String t = type == null ? "" : type;
        String d = detail == null ? "" : detail;
        String lower = d.toLowerCase(java.util.Locale.ROOT);
        String suffix = d.isEmpty() ? "." : ": " + d;
        if (code == 401 || "authentication_error".equals(t)) {
            return "Der API-Schlüssel wird abgelehnt" + (d.isEmpty() ? "." : " (" + d + ").")
                    + " Bitte in der Jarvis-App mit „Schlüssel testen“ prüfen.";
        }
        if (code == 403 || "permission_error".equals(t)) return "Der Schlüssel hat keine Berechtigung" + suffix;
        if (lower.contains("credit balance")) return "Das API-Guthaben ist aufgebraucht. Bitte in der Anthropic-Konsole aufladen.";
        if (code == 404 || "not_found_error".equals(t)) {
            return "Das eingestellte Modell ist nicht verfügbar. Lass das Modellfeld in der App leer" + suffix;
        }
        if (code == 429 || "rate_limit_error".equals(t)) return "Zu viele Anfragen in kurzer Zeit. Versuch es gleich noch einmal.";
        if (code == 529 || "overloaded_error".equals(t) || code >= 500) {
            return "Die Claude-Server sind gerade überlastet. Versuch es gleich noch einmal.";
        }
        if (code == 400) return "Die Anfrage wurde abgelehnt (Fehler 400)" + suffix;
        return "Fehler " + code + suffix;
    }

    /**
     * Was Jarvis sagt, wenn nach dem Bereinigen nichts übrig bleibt. Bestand die Antwort nur aus einer
     * [MERKEN:]-Zeile, ist „Notiert“ richtig – war sie leer, darf es nicht wie eine Bestätigung klingen.
     */
    static String replyOrFallback(String raw, String cleaned) {
        if (cleaned != null && !cleaned.isEmpty()) return cleaned;
        if (raw != null && raw.toUpperCase(java.util.Locale.ROOT).contains("[MERKEN")) return "Notiert, Sir.";
        return "Ich habe darauf keine Antwort bekommen. Frag mich bitte noch einmal.";
    }

    /**
     * Wörter, nach denen ein deutscher Satz sicher noch nicht zu Ende ist. Pronomen wie „du“ oder „es“
     * fehlen bewusst: „Was siehst du“ oder „Wie spät ist es“ sind fertige Fragen.
     */
    private static final java.util.Set<String> OPEN_ENDINGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "und", "oder", "aber", "denn", "dass", "weil", "wenn", "ob", "als", "wie", "wer", "wo", "wann",
            "warum", "welche", "welcher", "welches", "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen",
            "einem", "einer", "mein", "meine", "meinen", "dein", "deine", "mit", "für", "von", "zu", "zum", "zur", "auf",
            "in", "im", "an", "am", "bei", "nach", "über", "unter", "vor", "also", "ähm", "äh", "hm", "sondern",
            "bis", "seit", "damit", "sowie", "kannst", "könntest", "sollst", "willst", "möchte"));

    /** Ein einzelnes Wort davon ist noch keine Frage („Jarvis“, „ähm“ …). */
    private static final java.util.Set<String> ONLY_FILLER = new java.util.HashSet<>(java.util.Arrays.asList(
            "jarvis", "hey", "okay", "ok", "ähm", "äh", "hm", "also", "sir"));

    /**
     * Ob Art vermutlich mitten im Satz eine Denkpause macht. Dann hört Jarvis weiter zu,
     * sonst schickt er sofort ab – das spart bei normalen Fragen mehrere Sekunden.
     */
    static boolean looksUnfinished(String utterance) {
        if (utterance == null) return true;
        String t = utterance.trim().toLowerCase(java.util.Locale.GERMANY);
        if (t.isEmpty()) return true;
        if (t.endsWith("?") || t.endsWith("!") || t.endsWith(".")) return false;
        String[] words = t.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
        if (words.length == 0) return true;
        if (words.length == 1 && ONLY_FILLER.contains(words[0])) return true;
        return OPEN_ENDINGS.contains(words[words.length - 1]);
    }
}
