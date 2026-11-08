package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimingTest {
    @Test
    public void zerlegtDieZeitInAbschnitte() {
        Timing t = new Timing();
        t.speechEnded(1000);
        t.sent(1400);
        t.answered(3200);
        assertTrue(t.voiceStarted(3800));
        assertEquals("Zuhören 0,4 s · KI 1,8 s · Stimme 0,6 s · gesamt 2,8 s", t.summary());
    }

    @Test
    public void unvollstaendigeMessungWirdNichtGemeldet() {
        Timing t = new Timing();
        t.sent(100);
        assertFalse(t.voiceStarted(200)); // noch keine Antwort da (z. B. Begrüßung, Fehlermeldung)
    }

    @Test
    public void antippenOhneSprechendeZaehltAbAbschicken() {
        Timing t = new Timing();
        t.speechEndedIfUnknown(500);
        t.speechEndedIfUnknown(900); // erster Wert bleibt
        t.sent(500);
        t.answered(1500);
        assertTrue(t.voiceStarted(1700));
        assertEquals("Zuhören 0,0 s · KI 1,0 s · Stimme 0,2 s · gesamt 1,2 s", t.summary());
    }

    /** Streaming: Die KI-Zeit endet mit dem ersten Satz, nicht mit dem Ende der Antwort. */
    @Test
    public void ersterSatzZaehltBeimStreaming() {
        Timing t = new Timing();
        t.speechEnded(1000);
        t.sent(1200);
        t.firstAnswer(2000); // erster Satz da
        assertTrue(t.voiceStarted(2300));
        t.firstAnswer(4000); // Antwort komplett – ändert die Messung nicht mehr
        assertEquals("Zuhören 0,2 s · KI 0,8 s · Stimme 0,3 s · gesamt 1,3 s", t.summary());
    }
}
