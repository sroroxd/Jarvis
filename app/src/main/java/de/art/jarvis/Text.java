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
}
