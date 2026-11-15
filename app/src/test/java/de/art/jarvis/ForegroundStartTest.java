package de.art.jarvis;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForegroundStartTest {
    @Test public void fehlendeMikrofonberechtigungStartetNichts() {
        AtomicInteger calls = new AtomicInteger();
        String failure = ForegroundStart.attempt(false, calls::incrementAndGet);
        assertEquals(0, calls.get());
        assertTrue(failure.contains("Mikrofonberechtigung"));
        assertTrue(failure.contains("erneut starten"));
    }

    @Test public void whileInUseSperreWirdTrotzErteilterBerechtigungVerstaendlichAngezeigt() {
        AtomicInteger calls = new AtomicInteger();
        String failure = ForegroundStart.attempt(true, () -> {
            calls.incrementAndGet();
            throw new SecurityException("interner Android-Fehler mit Details, die nicht in die UI gehören");
        });
        assertEquals("Kein automatischer Wiederholungsversuch", 1, calls.get());
        assertTrue(failure.contains("Android"));
        assertTrue(failure.contains("Mikrofon"));
        assertTrue(failure.contains("Vordergrund"));
        assertFalse(failure.contains("interner"));
    }

    @Test public void hintergrundStartverbotWirdVerstaendlichAngezeigt() {
        String failure = ForegroundStart.attempt(true, () -> {
            throw new IllegalStateException("ForegroundServiceStartNotAllowedException");
        });
        assertTrue(failure.contains("Vordergrund"));
        assertTrue(failure.contains("erneut"));
        assertFalse(failure.contains("Exception"));
    }

    @Test public void erlaubterStartWirdGenauEinmalAusgefuehrt() {
        AtomicInteger calls = new AtomicInteger();
        assertNull(ForegroundStart.attempt(true, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void programmierfehlerWerdenNichtAlsBerechtigungsfehlerVersteckt() {
        ForegroundStart.attempt(true, () -> { throw new IllegalArgumentException("ungueltiger FGS-Typ"); });
    }
}
