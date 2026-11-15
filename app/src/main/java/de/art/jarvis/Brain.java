package de.art.jarvis;

/**
 * Wer antwortet? Reine Logik ohne Android, damit sie in den automatischen Tests läuft.
 *
 * Arts Vorgabe vom 15. Nov. 2026: keine kostenpflichtigen Aufrufe ohne ausdrückliche Zustimmung. Deshalb ist
 * „kostenlos“ der Standard: Mit Gemini-Schlüssel antwortet Gemini (kostenloses Kontingent), ohne Schlüssel die
 * Offline-Testantwort – damit lassen sich Zuhören, Antwort, „Stopp“ und Folgefragen ganz ohne Konto prüfen.
 * Claude (kostenpflichtig) antwortet nur, wenn Art ihn in den Einstellungen ausdrücklich wählt UND ein
 * Schlüssel eingetragen ist. Ein Claude-Schlüssel allein – etwa aus einer wiederhergestellten Sicherung –
 * löst nie einen bezahlten Aufruf aus.
 */
enum Brain {
    /** Lokale Testantwort ohne KI, ohne Netz, ohne Kosten. */
    OFFLINE_TEST,
    /** Google Gemini über das kostenlose Kontingent des Gemini-API-Schlüssels. */
    GEMINI,
    /** Anthropic Claude – kostenpflichtig, nur nach ausdrücklicher Wahl. */
    CLAUDE;

    static final String PROVIDER_FREE = "kostenlos";
    static final String PROVIDER_CLAUDE = "claude";

    static Brain choose(String provider, String claudeKey, String geminiKey) {
        if (PROVIDER_CLAUDE.equals(provider) && !empty(claudeKey)) return CLAUDE;
        if (!empty(geminiKey)) return GEMINI;
        return OFFLINE_TEST;
    }

    /** Darf dieser Weg Geld kosten? Nur Claude. ElevenLabs und alle Nebenanfragen hängen daran. */
    boolean paid() {
        return this == CLAUDE;
    }

    /** Kurzname für Startbildschirm und Testprotokoll. */
    String label() {
        switch (this) {
            case CLAUDE: return "Claude (kostenpflichtig)";
            case GEMINI: return "Gemini (kostenlos)";
            default: return "Offline-Test (ohne KI)";
        }
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
