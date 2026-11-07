package de.art.jarvis;

/**
 * Bildgrößen für das Bildschirmfoto. Reines Java ohne Android-Abhängigkeiten, daher automatisch testbar.
 *
 * <p>Claude-Modelle ab 4.7 (also auch Sonnet 5.5) verarbeiten Bilder bis 2576 Pixel an der langen Kante.
 * Im Spielmodus schickt Jarvis deshalb ein schärferes Bild (1920 statt 1280), damit Gold, Stufe, Händler
 * und kleine Gegenstands-Symbole lesbar bleiben. Im Alltag reicht 1280 (spart Tokens und Upload).
 */
final class CaptureSize {
    /** Lange Kante im Alltag. */
    static final int EVERYDAY_EDGE = 1280;
    /** Lange Kante im Spielmodus (TFT) – Gegenstände und Zahlen sind auf dem Handy sehr klein. */
    static final int GAME_EDGE = 1920;
    /** In dieser Größe läuft der Bildschirm-Mitschnitt dauerhaft – nie größer, als ein Foto braucht. */
    static final int MIRROR_EDGE = GAME_EDGE;

    static final int EVERYDAY_QUALITY = 75;
    /** Etwas weniger Kompression im Spiel, damit kleine Ziffern nicht verschwimmen. */
    static final int GAME_QUALITY = 82;

    private CaptureSize() {}

    static int edge(boolean game) {
        return game ? GAME_EDGE : EVERYDAY_EDGE;
    }

    static int quality(boolean game) {
        return game ? GAME_QUALITY : EVERYDAY_QUALITY;
    }

    /**
     * Breite und Höhe, verkleinert auf höchstens {@code maxEdge} an der langen Kante, Seitenverhältnis bleibt.
     * Gerade Zahlen, weil manche Grafiktreiber ungerade Puffergrößen nicht mögen. Nie größer als das Original.
     */
    static int[] fit(int width, int height, int maxEdge) {
        int w = Math.max(2, width);
        int h = Math.max(2, height);
        int longEdge = Math.max(w, h);
        if (longEdge > maxEdge) {
            float f = maxEdge / (float) longEdge;
            w = Math.round(w * f);
            h = Math.round(h * f);
        }
        return new int[]{even(w), even(h)};
    }

    /** Pixeldichte passend zur verkleinerten Breite (mindestens 1). */
    static int scaledDpi(int dpi, int originalWidth, int newWidth) {
        if (originalWidth <= 0) return Math.max(1, dpi);
        return Math.max(1, Math.round(dpi * (newWidth / (float) originalWidth)));
    }

    private static int even(int v) {
        int e = v - (v % 2);
        return Math.max(2, e);
    }
}
