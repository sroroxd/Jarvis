package de.art.jarvis;

/**
 * Wandelt die Gleitkomma-Samples der Offline-Stimme in 16 Bit um, mit kurzem Ein- und Ausblenden. Ohne das beginnt und
 * endet jeder Satz mit einem Sprung im Signal – hörbar als Knacken, besonders im Telefonmodus mit Echo-Unterdrückung.
 * Reines Java, daher automatisch testbar.
 */
final class PcmConvert {
    /** Einblenden am Satzanfang. */
    static final int FADE_IN_MS = 8;
    /** Ausblenden am Satzende (länger, weil ein Abbruch mitten im Ton stärker knackt). */
    static final int FADE_OUT_MS = 20;

    private PcmConvert() {}

    static short[] toPcm16(float[] samples, int rate) {
        short[] out = new short[samples.length];
        int fadeIn = Math.min(samples.length / 2, rate * FADE_IN_MS / 1000);
        int fadeOut = Math.min(samples.length / 2, rate * FADE_OUT_MS / 1000);
        for (int i = 0; i < samples.length; i++) {
            float v = samples[i];
            if (i < fadeIn) v *= i / (float) fadeIn;
            int fromEnd = samples.length - 1 - i;
            if (fromEnd < fadeOut) v *= fromEnd / (float) fadeOut;
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            out[i] = (short) Math.round(v * 32767f);
        }
        return out;
    }
}
