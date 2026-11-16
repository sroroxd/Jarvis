package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * Stellt den Gerätetest vom 16. Nov. nach (Build 185): Während Jarvis über OpenAI spricht, hörte der Server
 * Jarvis' eigenes Echo als neue Frage und antwortete sich selbst – bis in einen Schnellfeuer-Loop. Die
 * Schranke muss das Echo verwerfen, Arts lautes Dazwischenreden aber mit Satzanfang durchlassen.
 */
public class EchoGateTest {

    /** 20-ms-Block (480 Werte bei 24 kHz) mit fester Amplitude, little-endian PCM16. */
    private static byte[] frame(int amplitude) {
        byte[] b = new byte[960];
        for (int i = 0; i < 480; i++) {
            short v = (short) (((i & 1) == 0) ? amplitude : -amplitude);
            b[2 * i] = (byte) (v & 0xff);
            b[2 * i + 1] = (byte) (v >> 8);
        }
        return b;
    }

    private static final int ECHO = 1036;   // ≈ -30 dBFS: Rest von Jarvis' Stimme am Mikrofon
    private static final int VOICE = 5827;  // ≈ -15 dBFS: Art, deutlich über Schwelle (-30 + 10 dB)

    @Test public void ruheLaesstAllesDurch() {
        EchoGate gate = new EchoGate(SpeechGate.MARGIN_DB);
        List<byte[]> out = gate.feed(frame(ECHO), 960, 1000);
        assertEquals(1, out.size());
        assertEquals(960, out.get(0).length);
    }

    @Test public void echoWaehrendWiedergabeBleibtDrin() {
        EchoGate gate = new EchoGate(SpeechGate.MARGIN_DB);
        long now = 1000;
        for (int i = 0; i < 90; i++) { // 800 ms lernen + 1 s Echo, Wiedergabe läuft durchgehend
            gate.audible(now);
            assertTrue("Block " + i + " hätte verworfen werden müssen",
                    gate.feed(frame(ECHO), 960, now).isEmpty());
            now += 20;
        }
        assertTrue(gate.gating(now));
    }

    @Test public void dazwischenredenBrichtDurchMitVorlauf() {
        EchoGate gate = new EchoGate(SpeechGate.MARGIN_DB);
        long now = 1000;
        for (int i = 0; i < 60; i++) { gate.audible(now); gate.feed(frame(ECHO), 960, now); now += 20; }
        int sent = 0;
        boolean through = false;
        for (int i = 0; i < 15; i++) { // 300 ms laute Stimme
            gate.audible(now);
            List<byte[]> out = gate.feed(frame(VOICE), 960, now);
            sent += out.size();
            if (!out.isEmpty()) through = true;
            now += 20;
        }
        assertTrue("laute Stimme kam nicht durch", through);
        assertTrue("Satzanfang (Vorlauf) fehlt", sent >= SpeechGate.NEEDED);
        // Nach dem Durchbruch läuft alles weiter durch, auch leisere Blöcke desselben Satzes.
        gate.audible(now);
        assertEquals(1, gate.feed(frame(ECHO), 960, now).size());
        assertFalse(gate.gating(now));
    }

    @Test public void nachDerWiedergabeOeffnetSichDieSchranke() {
        EchoGate gate = new EchoGate(SpeechGate.MARGIN_DB);
        long now = 1000;
        for (int i = 0; i < 60; i++) { gate.audible(now); gate.feed(frame(ECHO), 960, now); now += 20; }
        now += EchoGate.TAIL_MS + 1; // Wiedergabe zu Ende, Nachlauf verstrichen
        assertEquals(1, gate.feed(frame(VOICE), 960, now).size());
        assertFalse(gate.gating(now));
    }

    @Test public void satzpauseVergisstDasEchoNicht() {
        EchoGate gate = new EchoGate(SpeechGate.MARGIN_DB);
        long now = 1000;
        for (int i = 0; i < 60; i++) { gate.audible(now); gate.feed(frame(ECHO), 960, now); now += 20; }
        now += 900; // Sprechpause zwischen zwei Sätzen, unter REARM_MS
        gate.feed(frame(ECHO), 960, now); now += 20;
        // Nächster Satz: ohne neues Lernen muss lautes Dazwischenreden binnen 300 ms durchkommen.
        boolean through = false;
        for (int i = 0; i < 15; i++) {
            gate.audible(now);
            if (!gate.feed(frame(VOICE), 960, now).isEmpty()) { through = true; break; }
            now += 20;
        }
        assertTrue("nach kurzer Satzpause war die Schranke wieder blind", through);
    }

    @Test public void langeStilleLerntNeu() {
        EchoGate gate = new EchoGate(SpeechGate.MARGIN_DB);
        long now = 1000;
        for (int i = 0; i < 60; i++) { gate.audible(now); gate.feed(frame(ECHO), 960, now); now += 20; }
        // Echte Stille: das Mikrofon liefert weiter Blöcke, die Wiedergabe schweigt.
        for (int i = 0; i < (EchoGate.REARM_MS + 500) / 20; i++) { gate.feed(frame(0), 960, now); now += 20; }
        // Neue Antwort: frisches Lernen – in den ersten 800 ms schlägt nichts an, auch laute Blöcke nicht.
        for (int i = 0; i < SpeechGate.WARMUP_FRAMES; i++) {
            gate.audible(now);
            assertTrue("Block " + i + " kam vor Ende des Lernens durch", gate.feed(frame(VOICE), 960, now).isEmpty());
            now += 20;
        }
    }

    @Test public void abbruchVerkuerztDenNachlauf() {
        EchoGate gate = new EchoGate(SpeechGate.MARGIN_DB);
        long now = 1000;
        gate.audible(now); // deckt bis now + TAIL_MS
        gate.flushed(now);
        now += 150; // nach dem Leeren der Wiedergabe reicht die Restlaufzeit von 120 ms
        assertEquals(1, gate.feed(frame(VOICE), 960, now).size());
    }
}
