package de.art.jarvis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Prüft die Bildgrößen für Bildschirmfotos (Galaxy S24 Ultra: 3120 × 1440). */
public class CaptureSizeTest {

    @Test
    public void tftImQuerformatWirdAufSpielgroesseVerkleinert() {
        int[] s = CaptureSize.fit(3120, 1440, CaptureSize.GAME_EDGE);
        assertEquals(1920, s[0]);
        assertEquals(886, s[1]); // 1440 × 1920/3120 = 886,15 → gerade
    }

    @Test
    public void alltagImHochformat() {
        int[] s = CaptureSize.fit(1440, 3120, CaptureSize.EVERYDAY_EDGE);
        assertEquals(1280, s[1]);
        assertEquals(590, s[0]); // 590,77 → 591 → gerade 590
    }

    @Test
    public void kleineBilderBleibenUnveraendert() {
        assertArrayEquals(new int[]{1080, 2400}, CaptureSize.fit(1080, 2400, 2576));
        assertArrayEquals(new int[]{1280, 590}, CaptureSize.fit(1280, 590, 1280));
    }

    @Test
    public void immerGeradeUndNieKleinerAlsZwei() {
        int[] s = CaptureSize.fit(1001, 333, 1000);
        assertEquals(0, s[0] % 2);
        assertEquals(0, s[1] % 2);
        assertArrayEquals(new int[]{2, 2}, CaptureSize.fit(0, -5, 1280));
    }

    @Test
    public void spielBildIstSchaerferAlsAlltagsbild() {
        assertTrue(CaptureSize.edge(true) > CaptureSize.edge(false));
        assertTrue(CaptureSize.quality(true) > CaptureSize.quality(false));
        // Der Dauer-Mitschnitt ist nie größer als das größte Foto (spart Akku und Wärme im Spiel).
        assertEquals(CaptureSize.GAME_EDGE, CaptureSize.MIRROR_EDGE);
        // Innerhalb der Grenze von Claude 4.7+ (2576 px lange Kante).
        assertTrue(CaptureSize.GAME_EDGE <= 2576);
    }

    @Test
    public void dpiWirdMitverkleinert() {
        assertEquals(308, CaptureSize.scaledDpi(500, 3120, 1920)); // 500 × 0,615 = 307,7
        assertEquals(1, CaptureSize.scaledDpi(1, 3000, 10));
        assertEquals(450, CaptureSize.scaledDpi(450, 0, 100));
    }
}
