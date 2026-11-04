package de.art.jarvis;

import java.util.Locale;

/**
 * Kostenzähler für den Gesprächsmodus. Reine Java-Logik, damit prüfbar.
 *
 * Preise laut Google (Gemini Live API, Stand Nov. 2026), bezahlte Stufe:
 *   Audio hinein  0,005 $/Min  (3 $/1M Token)
 *   Audio hinaus  0,018 $/Min  (12 $/1M Token)
 *   Bild/Video    0,002 $/Min  (1 $/1M Token)
 *
 * Das Ergebnis ist eine Schätzung: Google rechnet nach Token, die Minutenpreise
 * sind Googles eigene Umrechnung für einen durchgehenden Strom.
 */
final class LiveCost {
    static final double USD_PER_MIN_AUDIO_IN = 0.005;
    static final double USD_PER_MIN_AUDIO_OUT = 0.018;
    static final double USD_PER_MIN_VIDEO_IN = 0.002;

    private long micMs;
    private long playMs;
    private long videoMs;

    void reset() {
        micMs = 0;
        playMs = 0;
        videoMs = 0;
    }

    void addMic(long ms) {
        if (ms > 0) micMs += ms;
    }

    void addPlayback(long ms) {
        if (ms > 0) playMs += ms;
    }

    void addVideo(long ms) {
        if (ms > 0) videoMs += ms;
    }

    /** Dauer eines PCM-Blocks (16 Bit, mono) in Millisekunden. */
    static long pcmMs(int bytes, int sampleRate) {
        if (bytes <= 0 || sampleRate <= 0) return 0;
        return bytes * 1000L / (2L * sampleRate);
    }

    long micMs() { return micMs; }

    long playbackMs() { return playMs; }

    long videoMs() { return videoMs; }

    double usd() {
        return micMs / 60000.0 * USD_PER_MIN_AUDIO_IN
                + playMs / 60000.0 * USD_PER_MIN_AUDIO_OUT
                + videoMs / 60000.0 * USD_PER_MIN_VIDEO_IN;
    }

    /** Zeile für den Startbildschirm, z. B. „Gespräch 4:12 · ca. 0,06 $“. */
    String summary() {
        return "Gespräch " + clock(micMs) + " · ca. " + money(usd());
    }

    /** mm:ss, bei langen Gesprächen h:mm:ss. */
    static String clock(long ms) {
        long total = Math.max(0, ms) / 1000;
        long h = total / 3600;
        long m = total % 3600 / 60;
        long s = total % 60;
        return h > 0
                ? String.format(Locale.GERMANY, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.GERMANY, "%d:%02d", m, s);
    }

    /**
     * Betrag mit deutschem Komma. Unter einem Cent zeigt zwei Nachkommastellen
     * keinen Wert, deshalb dort drei Stellen.
     */
    static String money(double usd) {
        return usd > 0 && usd < 0.01
                ? String.format(Locale.GERMANY, "%.3f $", usd)
                : String.format(Locale.GERMANY, "%.2f $", usd);
    }
}
