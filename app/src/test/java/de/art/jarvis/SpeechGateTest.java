package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

/** Dazwischenreden: Art von Jarvis' eigenem Echo unterscheiden. */
public class SpeechGateTest {

    /** Ein 20-ms-Block Rauschen mit ungefähr dem gewünschten Pegel. */
    private static short[] block(double db, Random rnd) {
        double rms = Math.pow(10, db / 20) * 32768;
        short[] s = new short[SpeechGate.FRAME];
        for (int i = 0; i < s.length; i++) {
            double v = rnd.nextGaussian() * rms;
            s[i] = (short) Math.max(-32768, Math.min(32767, v));
        }
        return s;
    }

    /** Füttert n Blöcke, gibt zurück, nach wie vielen es angeschlagen hat (-1 = gar nicht). */
    private static int feed(SpeechGate g, double db, double jitter, int n, Random rnd) {
        for (int i = 0; i < n; i++) {
            double d = db + (rnd.nextDouble() * 2 - 1) * jitter;
            short[] b = block(d, rnd);
            if (g.feed(b, 0, b.length)) return i;
        }
        return -1;
    }

    @Test
    public void pegelstimmt() {
        Random rnd = new Random(1);
        short[] b = block(-20, rnd);
        assertEquals(-20, SpeechGate.dbfs(b, 0, b.length), 1.0);
        assertEquals(-90, SpeechGate.dbfs(new short[320], 0, 320), 0.01);
    }

    @Test
    public void inDerAufwaermphaseSchlaegtNichtsAn() {
        Random rnd = new Random(2);
        SpeechGate g = new SpeechGate();
        // Selbst laute Sprache sofort am Anfang: erst wird gelernt.
        assertEquals(-1, feed(g, -15, 0, SpeechGate.WARMUP_FRAMES, rnd));
    }

    @Test
    public void eigenesEchoLoestNichtAus() {
        Random rnd = new Random(3);
        SpeechGate g = new SpeechGate();
        // Lautsprecher ohne gute Echo-Unterdrückung: Jarvis kommt mit -30 dB ±4 dB zurück.
        feed(g, -30, 4, SpeechGate.WARMUP_FRAMES, rnd);
        assertEquals(-1, feed(g, -30, 4, 500, rnd)); // 10 Sekunden weiter sprechen: kein Fehlalarm
    }

    @Test
    public void artUeberDemEchoWirdErkannt() {
        Random rnd = new Random(4);
        SpeechGate g = new SpeechGate();
        feed(g, -30, 4, SpeechGate.WARMUP_FRAMES, rnd);
        feed(g, -30, 4, 50, rnd);
        int after = feed(g, -12, 3, 40, rnd); // Art spricht direkt ins Handy
        assertTrue("erkannt nach " + after + " Blöcken", after >= 0 && after < 20); // unter 400 ms
    }

    @Test
    public void inRuhigerUmgebungMitKopfhoerern() {
        Random rnd = new Random(5);
        SpeechGate g = new SpeechGate();
        // Ton über Kopfhörer: am Handy-Mikrofon nur Raumrauschen.
        feed(g, -60, 3, SpeechGate.WARMUP_FRAMES, rnd);
        assertEquals(SpeechGate.MIN_SPEECH_DB, g.thresholdDb(), 0.01);
        assertTrue(feed(g, -28, 4, 40, rnd) >= 0);
    }

    @Test
    public void kurzesKlopfenIstKeinSprechen() {
        Random rnd = new Random(6);
        SpeechGate g = new SpeechGate();
        feed(g, -55, 2, SpeechGate.WARMUP_FRAMES, rnd);
        assertEquals(-1, feed(g, -10, 0, 4, rnd));   // 80 ms laut
        assertEquals(-1, feed(g, -55, 2, 40, rnd));  // dann wieder still
    }

    @Test
    public void resetLerntNeu() {
        Random rnd = new Random(7);
        SpeechGate g = new SpeechGate();
        feed(g, -20, 2, SpeechGate.WARMUP_FRAMES, rnd);
        double laut = g.thresholdDb();
        g.reset();
        feed(g, -60, 2, SpeechGate.WARMUP_FRAMES, rnd);
        assertTrue(g.thresholdDb() < laut);
        assertFalse(g.feed(block(-60, rnd), 0, SpeechGate.FRAME));
    }
}
