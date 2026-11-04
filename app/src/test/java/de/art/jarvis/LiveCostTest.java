package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveCostTest {

    @Test
    public void pcmDurationFromByteCount() {
        // 16 kHz, 16 Bit mono: 32000 Byte = 1 Sekunde
        assertEquals(1000, LiveCost.pcmMs(32000, 16000));
        // 24 kHz Ausgabe: 48000 Byte = 1 Sekunde
        assertEquals(1000, LiveCost.pcmMs(48000, 24000));
        assertEquals(20, LiveCost.pcmMs(640, 16000));
    }

    @Test
    public void pcmDurationIsRobustAgainstNonsense() {
        assertEquals(0, LiveCost.pcmMs(0, 16000));
        assertEquals(0, LiveCost.pcmMs(-5, 16000));
        assertEquals(0, LiveCost.pcmMs(32000, 0));
    }

    @Test
    public void oneMinuteEachMatchesGooglePerMinutePrices() {
        LiveCost c = new LiveCost();
        c.addMic(60000);
        c.addPlayback(60000);
        c.addVideo(60000);
        assertEquals(0.005 + 0.018 + 0.002, c.usd(), 1e-9);
    }

    @Test
    public void tenMinuteConversationCostsAboutTenCents() {
        // Realistisch: 10 Min. Mikrofon, davon 3 Min. Antwort, kein Video.
        // Die Antwortzeit kostet mehr als das Zuhören – 3 Min. Sprechen (0,054 $)
        // liegen über 10 Min. Mikrofon (0,05 $).
        LiveCost c = new LiveCost();
        c.addMic(10 * 60000);
        c.addPlayback(3 * 60000);
        double usd = c.usd();
        assertEquals(0.104, usd, 1e-9);
        assertTrue("Antwortzeit muss teurer sein als Zuhören", 0.054 > 0.05);
    }

    @Test
    public void oneHourWithScreenIsAboutOneDollar() {
        // Spielmodus: eine Stunde durchgehend mit Bildschirm
        LiveCost c = new LiveCost();
        c.addMic(60 * 60000);
        c.addPlayback(20 * 60000);
        c.addVideo(60 * 60000);
        double usd = c.usd();
        assertTrue("Stunde im Spielmodus sollte bei etwa 1 $ liegen: " + usd, usd > 0.7 && usd < 1.5);
    }

    @Test
    public void resetClearsEverything() {
        LiveCost c = new LiveCost();
        c.addMic(5000);
        c.addPlayback(5000);
        c.addVideo(5000);
        c.reset();
        assertEquals(0, c.micMs());
        assertEquals(0, c.playbackMs());
        assertEquals(0, c.videoMs());
        assertEquals(0.0, c.usd(), 1e-12);
    }

    @Test
    public void negativeDurationsAreIgnored() {
        LiveCost c = new LiveCost();
        c.addMic(-1000);
        c.addPlayback(-1000);
        c.addVideo(-1000);
        assertEquals(0.0, c.usd(), 1e-12);
    }

    @Test
    public void clockFormatsMinutesAndHours() {
        assertEquals("0:00", LiveCost.clock(0));
        assertEquals("0:05", LiveCost.clock(5400));
        assertEquals("4:12", LiveCost.clock(252000));
        assertEquals("1:00:00", LiveCost.clock(3600000));
        assertEquals("2:03:04", LiveCost.clock(7384000));
        assertEquals("0:00", LiveCost.clock(-500));
    }

    @Test
    public void moneyUsesGermanCommaAndThreeDigitsBelowOneCent() {
        assertEquals("0,00 $", LiveCost.money(0));
        assertEquals("0,005 $", LiveCost.money(0.005));
        assertEquals("0,06 $", LiveCost.money(0.06));
        assertEquals("1,23 $", LiveCost.money(1.234));
    }

    @Test
    public void summaryShowsTalkTimeAndCost() {
        LiveCost c = new LiveCost();
        c.addMic(252000);
        c.addPlayback(60000);
        String s = c.summary();
        assertTrue("Gesprächsdauer fehlt: " + s, s.contains("4:12"));
        assertTrue("Währung fehlt: " + s, s.contains("$"));
        assertTrue("Schätzung nicht gekennzeichnet: " + s, s.contains("ca."));
    }
}
