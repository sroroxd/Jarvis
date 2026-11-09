package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Umwandlung der Offline-Stimme in 16 Bit mit Ein- und Ausblenden (gegen das Knacken zwischen den Sätzen). */
public class PcmConvertTest {

    private static float[] konstant(int n, float v) {
        float[] a = new float[n];
        java.util.Arrays.fill(a, v);
        return a;
    }

    @Test
    public void beginntUndEndetLeise() {
        short[] s = PcmConvert.toPcm16(konstant(24000, 0.5f), 24000);
        assertEquals(24000, s.length);
        assertEquals(0, s[0]);
        assertEquals(0, s[s.length - 1]);
        assertEquals(16384, s[12000], 1); // Mitte unverändert: 0,5 · 32767
    }

    @Test
    public void einblendenDauertAchtMillisekunden() {
        short[] s = PcmConvert.toPcm16(konstant(24000, 1f), 24000);
        int fade = 24000 * PcmConvert.FADE_IN_MS / 1000; // 192 Samples
        assertTrue(s[fade / 2] > 0 && s[fade / 2] < 32767);
        assertEquals(32767, s[fade + 5]);
    }

    @Test
    public void uebersteuerungWirdBegrenzt() {
        short[] s = PcmConvert.toPcm16(konstant(24000, 3f), 24000);
        assertEquals(32767, s[12000]);
        short[] n = PcmConvert.toPcm16(konstant(24000, -3f), 24000);
        assertEquals(-32767, n[12000]);
    }

    @Test
    public void sehrKurzeSaetzeUndLeereEingabe() {
        assertEquals(0, PcmConvert.toPcm16(new float[0], 24000).length);
        short[] kurz = PcmConvert.toPcm16(konstant(10, 1f), 24000);
        assertEquals(10, kurz.length);
        assertEquals(0, kurz[0]);
    }
}
