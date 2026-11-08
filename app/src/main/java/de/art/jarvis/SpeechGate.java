package de.art.jarvis;

/**
 * Erkennt, ob Art zu sprechen beginnt, während Jarvis selbst spricht („Dazwischenreden“).
 *
 * Reine Java-Logik ohne Android, damit sie in den automatischen Tests läuft.
 *
 * Das Problem: Das Mikrofon hört auch Jarvis' eigene Stimme. Selbst mit der Echo-Unterdrückung des
 * Handys bleibt ein Rest. Deshalb lernt die Schranke, wie laut dieser Rest ist, und schlägt erst an,
 * wenn es deutlich lauter wird – und zwar nicht nur kurz (Klopfen, Klick), sondern über gut eine
 * Viertelsekunde hinweg.
 *
 * Zwei Fehler der ersten Fassung, beide in der Simulation nachgestellt (siehe SpeechGateTest):
 * - Gelernt wurde ab dem ersten Block. Kam die Stimme erst später (Sprachausgabe braucht einen Moment,
 *   ElevenLabs lädt erst), lernte die Schranke Stille – und Jarvis' erster Satz brach sie auf.
 *   Jetzt beginnt das Lernen erst mit {@link #voiceStarted()} (spätestens nach {@link #MAX_WAIT_FRAMES}).
 * - In jeder Sprechpause zwischen zwei Sätzen sank die Echo-Schätzung Richtung Stille. Ab etwa 300 ms
 *   Pause hielt die Schranke den nächsten Satz für Art und brach ab („liest nur den ersten Satz vor“,
 *   Wünsche #11, #18, #20). Jetzt sinkt die Schätzung nie; sie steigt nur, wenn das Echo lauter wird.
 *
 * Arbeitet in Blöcken von 20 ms (320 Werte bei 16 kHz).
 */
final class SpeechGate {
    static final int SAMPLE_RATE = 16000;
    /** 20 ms. */
    static final int FRAME = 320;
    /** 800 ms lang den Pegel des eigenen Echos lernen, bevor überhaupt etwas anschlagen kann. */
    static final int WARMUP_FRAMES = 40;
    /**
     * Kommt kein Startsignal der Stimme, beginnt das Lernen spätestens nach 5 s – dann ist sie sicher zu hören.
     * Bis dahin schlägt nichts an.
     */
    static final int MAX_WAIT_FRAMES = 250;
    /** Betrachtetes Fenster: 300 ms … */
    static final int WINDOW = 15;
    /** … davon müssen mindestens 260 ms laut sein. */
    static final int NEEDED = 13;
    /**
     * So viel lauter als das gelernte Echo muss Art sein. Die 14 dB der Vorversion waren eine Reaktion auf
     * Selbstabbrüche, deren eigentliche Ursache die sinkende Echo-Schätzung war. Mit festgehaltener
     * Schätzung reichen 10 dB – Art muss nicht mehr schreien, um Jarvis zu unterbrechen (Wünsche #17, #19).
     */
    static final double MARGIN_DB = 10;
    /** Darunter ist es auf jeden Fall keine Stimme direkt am Handy. */
    static final double MIN_SPEECH_DB = -40;
    /** Wie schnell die Echo-Schätzung einem lauter werdenden Echo folgt (je 20-ms-Block). */
    static final double RISE = 0.1;
    /** Standardstufe des Reglers „Empfindlichkeit beim Dazwischenreden“ (entspricht {@link #MARGIN_DB}). */
    static final int DEFAULT_LEVEL = 5;

    /**
     * Abstand zum Echo für eine Reglerstufe (Wunsch #25): 1 = unempfindlich (14,8 dB, unterbricht sich praktisch nie
     * selbst), 5 = Standard (10 dB), 10 = sehr empfindlich (4 dB, reagiert schon auf leises Sprechen).
     */
    static double marginFor(int level) {
        int l = Math.max(1, Math.min(10, level));
        return MARGIN_DB + (DEFAULT_LEVEL - l) * 1.2;
    }

    private final double margin;

    SpeechGate() {
        this(MARGIN_DB);
    }

    SpeechGate(double marginDb) {
        margin = marginDb;
    }

    private final double[] warmup = new double[WARMUP_FRAMES];
    private final boolean[] window = new boolean[WINDOW];
    /** Wird vom Haupt-Thread gesetzt, gelesen vom Aufnahme-Thread. */
    private volatile boolean voiceStarted;
    private int waited;
    private int learned;
    private int windowPos;
    private int loudInWindow;
    private double echoDb = -90;

    /** Setzt alles zurück – bei jeder neuen Antwort, denn Lautstärke und Umgebung ändern sich. */
    void reset() {
        voiceStarted = false;
        waited = 0;
        learned = 0;
        windowPos = 0;
        loudInWindow = 0;
        echoDb = -90;
        java.util.Arrays.fill(window, false);
    }

    /** Jarvis' Stimme ist gerade angelaufen: ab jetzt das Echo lernen. Darf von jedem Thread kommen. */
    void voiceStarted() {
        voiceStarted = true;
    }

    /** Hat die Schranke fertig gelernt und kann anschlagen? */
    boolean armed() {
        return learned >= WARMUP_FRAMES;
    }

    /** Gelerntes Echo in dBFS (für die Messanzeige). */
    double echoDb() {
        return echoDb;
    }

    /**
     * Messzeile für den Startbildschirm, damit ein Gerätetest zeigt, woran das Dazwischenreden hängt:
     * Liegt der lauteste Moment (auch Arts Stimme) unter der Schwelle, kam Art nicht über Jarvis' Echo.
     */
    static String report(boolean armed, double echoDb, double thresholdDb, double peakDb, boolean echoFilter, String trigger) {
        if (!armed) return "zu kurz zum Messen";
        return String.format(java.util.Locale.GERMANY, "Echo %.0f dB · Schwelle %.0f dB · lautester Moment %.0f dB · "
                        + "Echo-Filter %s · unterbrochen: %s",
                echoDb, thresholdDb, peakDb, echoFilter ? "an" : "aus", trigger == null ? "nein" : trigger);
    }

    /** Aktuelle Schwelle in dBFS (für Anzeige und Tests). */
    double thresholdDb() {
        return Math.max(echoDb + margin, MIN_SPEECH_DB);
    }

    /**
     * Ein 20-ms-Block. @return {@code true}, sobald Art spricht.
     */
    boolean feed(short[] pcm, int offset, int length) {
        double db = dbfs(pcm, offset, length);
        if (learned < WARMUP_FRAMES) {
            // Vor dem Start der Stimme nichts lernen – sonst hält die Schranke Stille für das Echo.
            if (!voiceStarted && waited < MAX_WAIT_FRAMES) {
                waited++;
                return false;
            }
            warmup[learned++] = db;
            if (learned == WARMUP_FRAMES) echoDb = percentile(warmup, 0.95);
            return false;
        }
        boolean loud = db >= thresholdDb();
        // Wird das Echo lauter (Jarvis spricht lauter, Spielsound), zieht die Schätzung nach – aber nur mit
        // Blöcken unter der Schwelle, sonst lernte sie Arts Stimme als „Echo“. Nach unten geht sie NIE:
        // Sprechpausen zwischen Sätzen sind leise und ließen die Schwelle sonst bis zum nächsten Satz absacken.
        if (!loud && db > echoDb) echoDb += RISE * (db - echoDb);
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
