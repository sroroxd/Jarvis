package de.art.jarvis.neubau;

/** Nur für den ersten kostenlosen Audio-Gerätetest, ausdrücklich KEINE KI. */
final class AudioProbe {
    private AudioProbe() {}

    static String reply(String recognized) {
        if (recognized == null || recognized.trim().isEmpty()) {
            return "Ich habe nichts verstanden.";
        }
        return "Sprachtest erfolgreich. Du hast gesagt: " + recognized.trim();
    }
}
