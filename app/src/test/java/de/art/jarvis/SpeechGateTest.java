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

    /** Schranke, deren Stimme gerade angelaufen ist – wie im Dienst nach dem Start der Sprachausgabe. */
    private static SpeechGate started() {
        SpeechGate g = new SpeechGate();
        g.voiceStarted();
        return g;
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
        SpeechGate g = started();
        // Selbst laute Sprache sofort am Anfang: erst wird gelernt.
        assertEquals(-1, feed(g, -15, 0, SpeechGate.WARMUP_FRAMES, rnd));
        assertTrue(g.armed());
    }

    @Test
    public void eigenesEchoLoestNichtAus() {
        Random rnd = new Random(3);
        SpeechGate g = started();
        // Lautsprecher ohne gute Echo-Unterdrückung: Jarvis kommt mit -30 dB ±4 dB zurück.
        feed(g, -30, 4, SpeechGate.WARMUP_FRAMES, rnd);
        assertEquals(-1, feed(g, -30, 4, 500, rnd)); // 10 Sekunden weiter sprechen: kein Fehlalarm
    }

    @Test
    public void artUeberDemEchoWirdErkannt() {
        Random rnd = new Random(4);
        SpeechGate g = started();
        feed(g, -30, 4, SpeechGate.WARMUP_FRAMES, rnd);
        feed(g, -30, 4, 50, rnd);
        int after = feed(g, -12, 3, 40, rnd); // Art spricht direkt ins Handy
        assertTrue("erkannt nach " + after + " Blöcken", after >= 0 && after < 20); // unter 400 ms
    }

    @Test
    public void artInNormalerLautstaerkeWirdErkannt() {
        Random rnd = new Random(9);
        SpeechGate g = started();
        // Echo-Rest nach der Unterdrückung -36 dB, Art spricht normal laut (-20 dB) – kein Schreien nötig.
        // Mit den 14 dB der Vorversion lag die Schwelle hier bei etwa -18 dB: Art wäre nicht durchgekommen.
        feed(g, -36, 4, SpeechGate.WARMUP_FRAMES, rnd);
        feed(g, -36, 4, 50, rnd);
        int after = feed(g, -20, 3, 40, rnd);
        assertTrue("erkannt nach " + after + " Blöcken", after >= 0 && after < 20);
    }

    @Test
    public void inRuhigerUmgebungMitKopfhoerern() {
        Random rnd = new Random(5);
        SpeechGate g = started();
        // Ton über Kopfhörer: am Handy-Mikrofon nur Raumrauschen.
        feed(g, -60, 3, SpeechGate.WARMUP_FRAMES, rnd);
        assertEquals(SpeechGate.MIN_SPEECH_DB, g.thresholdDb(), 0.01);
        assertTrue(feed(g, -28, 4, 40, rnd) >= 0);
    }

    @Test
    public void kurzesKlopfenIstKeinSprechen() {
        Random rnd = new Random(6);
        SpeechGate g = started();
        feed(g, -55, 2, SpeechGate.WARMUP_FRAMES, rnd);
        assertEquals(-1, feed(g, -10, 0, 4, rnd));   // 80 ms laut
        assertEquals(-1, feed(g, -55, 2, 40, rnd));  // dann wieder still
    }

    @Test
    public void resetLerntNeu() {
        Random rnd = new Random(7);
        SpeechGate g = started();
        feed(g, -20, 2, SpeechGate.WARMUP_FRAMES, rnd);
        double laut = g.thresholdDb();
        g.reset();
        assertFalse(g.armed());
        g.voiceStarted();
        feed(g, -60, 2, SpeechGate.WARMUP_FRAMES, rnd);
        assertTrue(g.thresholdDb() < laut);
        assertFalse(g.feed(block(-60, rnd), 0, SpeechGate.FRAME));
    }

    @Test
    public void lauterWerdendeStimmeUndSpielsoundLoesenNichtAus() {
        Random rnd = new Random(8);
        SpeechGate g = started();
        feed(g, -35, 3, SpeechGate.WARMUP_FRAMES, rnd);
        // Der zweite Satz kommt 10 dB lauter zurück (Echo/Spielsound): kein Abbruch.
        assertEquals(-1, feed(g, -25, 3, 300, rnd));
    }

    // ------------------------------------------------------------ Regressionen aus den Wünschen #11, #18, #20

    /**
     * Der Fehler hinter „liest nur den ersten Satz vor und bricht dann ab“: In der Sprechpause zwischen zwei
     * Sätzen sank die Echo-Schätzung, der gleich laute zweite Satz galt dann als Art. Die alte Fassung brach
     * hier ab 300 ms Pause in praktisch jedem Lauf ab (199 von 200 Läufen in der Simulation).
     */
    @Test
    public void sprechpauseZwischenSaetzenLoestNichtAus() {
        for (int pause : new int[]{10, 15, 25, 40, 75}) { // 200 ms bis 1,5 s
            for (int seed = 0; seed < 50; seed++) {
                Random rnd = new Random(seed);
                SpeechGate g = started();
                feed(g, -30, 4, SpeechGate.WARMUP_FRAMES, rnd);       // erster Satz: Echo lernen
                assertEquals(-1, feed(g, -30, 4, 60, rnd));           // Rest des ersten Satzes
                assertEquals(-1, feed(g, -65, 2, pause, rnd));        // Sprechpause
                assertEquals("Pause " + pause * 20 + " ms, Lauf " + seed,
                        -1, feed(g, -30, 4, 150, rnd));               // zweiter Satz, gleich laut
            }
        }
    }

    /** Nach der Pause unterbricht Art trotzdem sofort. */
    @Test
    public void artNachSprechpauseWirdErkannt() {
        Random rnd = new Random(11);
        SpeechGate g = started();
        feed(g, -30, 4, SpeechGate.WARMUP_FRAMES, rnd);
        feed(g, -65, 2, 40, rnd);
        int after = feed(g, -14, 3, 40, rnd);
        assertTrue("erkannt nach " + after + " Blöcken", after >= 0 && after < 20);
    }

    /**
     * Die Stimme setzt verzögert ein (Sprachausgabe startet, ElevenLabs lädt): Vor dem Startsignal wird weder
     * gelernt noch angeschlagen. Die alte Fassung lernte hier Stille und brach beim ersten Wort ab.
     */
    @Test
    public void verzoegerterStimmstartLoestNichtAus() {
        for (int seed = 0; seed < 50; seed++) {
            Random rnd = new Random(100 + seed);
            SpeechGate g = new SpeechGate();
            assertEquals(-1, feed(g, -65, 2, 60, rnd));      // 1,2 s Stille, bevor die Stimme kommt
            assertFalse(g.armed());
            g.voiceStarted();
            assertEquals("Lauf " + seed, -1, feed(g, -30, 4, 200, rnd));
        }
    }

    /** Vor dem Startsignal schlägt selbst sehr Lautes nicht an (Jarvis' eigener Satzanfang). */
    @Test
    public void vorDemStartsignalSchlaegtNichtsAn() {
        Random rnd = new Random(12);
        SpeechGate g = new SpeechGate();
        assertEquals(-1, feed(g, -10, 0, 100, rnd));
        assertFalse(g.armed());
    }

    /** Fehlt das Startsignal ganz, lernt die Schranke nach der Wartezeit trotzdem und wird scharf. */
    @Test
    public void ohneStartsignalLerntSieNachDerWartezeit() {
        Random rnd = new Random(13);
        SpeechGate g = new SpeechGate();
        assertEquals(-1, feed(g, -30, 4, SpeechGate.MAX_WAIT_FRAMES + SpeechGate.WARMUP_FRAMES, rnd));
        assertTrue(g.armed());
        assertTrue(feed(g, -12, 3, 40, rnd) >= 0);
    }
}
