package de.art.jarvis;

/**
 * Erkennt, ob Art zu sprechen beginnt, während Jarvis selbst spricht („Dazwischenreden“).
 *
 * Reine Java-Logik ohne Android, damit sie in den automatischen Tests läuft.
 *
 * Das Problem: Das Mikrofon hört auch Jarvis' eigene Stimme. Selbst mit der Echo-Unterdrückung des
 * Handys bleibt ein Rest. Deshalb lernt die Schranke in der ersten halben Sekunde, wie laut dieser
 * Rest ist, und schlägt erst an, wenn es deutlich lauter wird – und zwar nicht nur kurz (Klopfen,
 * Klick), sondern über gut eine Viertelsekunde hinweg.
 *
 * Arbeitet in Blöcken von 20 ms (320 Werte bei 16 kHz).
 */
final class SpeechGate {
    static final int SAMPLE_RATE = 16000;
    /** 20 ms. */
    static final int FRAME = 320;
    /** 800 ms lang den Pegel des eigenen Echos lernen, bevor überhaupt etwas anschlagen kann. */
    static final int WARMUP_FRAMES = 40;
    /** Betrachtetes Fenster: 300 ms … */
    static final int WINDOW = 15;
    /** … davon müssen mindestens 260 ms laut sein. */
    static final int NEEDED = 13;
    /**
     * So viel lauter als das gelernte Echo muss Art sein. 14 dB statt 10: Jarvis' Sätze und der
     * Spielsound schwanken um 10 dB, das darf nicht als Art zählen (Wunsch #10, #11).
     */
    static final double MARGIN_DB = 14;
    /** Darunter ist es auf jeden Fall keine Stimme direkt am Handy. */
    static final double MIN_SPEECH_DB = -40;

    private final double[] warmup = new double[WARMUP_FRAMES];
    private final boolean[] window = new boolean[WINDOW];
    private int frames;
    private int windowPos;
    private int loudInWindow;
    private double echoDb = -90;

    /** Setzt alles zurück – bei jeder neuen Antwort, denn Lautstärke und Umgebung ändern sich. */
    void reset() {
        frames = 0;
        windowPos = 0;
        loudInWindow = 0;
        echoDb = -90;
        java.util.Arrays.fill(window, false);
    }

    /** Aktuelle Schwelle in dBFS (für Anzeige und Tests). */
    double thresholdDb() {
        return Math.max(echoDb + MARGIN_DB, MIN_SPEECH_DB);
    }

    /**
     * Ein 20-ms-Block. @return {@code true}, sobald Art spricht.
     */
    boolean feed(short[] pcm, int offset, int length) {
        double db = dbfs(pcm, offset, length);
        if (frames < WARMUP_FRAMES) {
            warmup[frames++] = db;
            if (frames == WARMUP_FRAMES) echoDb = percentile(warmup, 0.95);
            return false;
        }
        frames++;
        boolean loud = db >= thresholdDb();
        // Leise Blöcke führen die Echo-Schätzung langsam nach (Jarvis spricht mal lauter, mal leiser).
        // Laute Blöcke nicht – sonst lernte die Schranke Arts Stimme als „Echo“.
        if (!loud) echoDb = 0.95 * echoDb + 0.05 * db;
        if (window[windowPos]) loudInWindow--;
        window[windowPos] = loud;
        if (loud) loudInWindow++;
        windowPos = (windowPos + 1) % WINDOW;
        return loudInWindow >= NEEDED;
    }

    /** Pegel eines Blocks in dBFS (0 = Vollaussteuerung, -90 = Stille). */
    static double dbfs(short[] pcm, int offset, int length) {
        if (length <= 0) return -90;
        double sum = 0;
        for (int i = offset; i < offset + length; i++) {
            double v = pcm[i] / 32768.0;
            sum += v * v;
        }
        double rms = Math.sqrt(sum / length);
        return rms <= 0.0000316 ? -90 : 20 * Math.log10(rms); // -90 dB als Untergrenze
    }

    private static double percentile(double[] values, double q) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int idx = (int) Math.min(sorted.length - 1, Math.round(q * (sorted.length - 1)));
        return sorted[idx];
    }
}
