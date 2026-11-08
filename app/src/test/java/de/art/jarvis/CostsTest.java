package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Kosten pro Anfrage – Preise laut Anthropic-Preisliste vom 8. Nov. 2026. */
public class CostsTest {

    @Test
    public void typischeSpielfrageMitSonnet() {
        // 6.000 Tokens Grundanweisung aus dem Zwischenspeicher, 2.500 Tokens Bild und Frage, 60 Tokens Antwort.
        double usd = Costs.usd("claude-sonnet-5-5", 2500, 0, 6000, 60, 0);
        assertEquals(2500 * 2.0e-6 + 6000 * 0.10e-6 + 60 * 10e-6, usd, 1e-12);
        assertEquals("0,6 ct", Costs.cents(usd));
    }

    @Test
    public void waechterMitHaikuKostetFastNichts() {
        // Ein Blick des Phasen-Wächters: Bild in Alltagsgröße und Frage, ein paar Wörter Antwort.
        double usd = Costs.usd("claude-haiku-5-5", 1200, 0, 0, 6, 0);
        assertEquals(1200 * 0.10e-6 + 6 * 0.50e-6, usd, 1e-12);
        assertEquals("0,0 ct", Costs.cents(usd));
        // 350 Blicke in einer Partie von 35 Minuten: rund 4 Cent.
        assertEquals("4,3 ct", Costs.cents(usd * 350));
    }

    @Test
    public void cacheSchreibenUndWebsuche() {
        double usd = Costs.usd("claude-haiku-5-5", 1000, 6000, 0, 800, 3);
        assertEquals(1000 * 0.10e-6 + 6000 * 0.125e-6 + 800 * 0.50e-6 + 0.03, usd, 1e-12);
        assertEquals("3,1 ct", Costs.cents(usd));
        assertEquals("12 ct", Costs.cents(0.1234));
    }

    @Test
    public void unbekanntesModellZeigtNichts() {
        assertFalse(Costs.known("gemini-2.5-flash"));
        assertEquals(0, Costs.usd("gemini-2.5-flash", 1000, 0, 0, 100, 0), 0);
        assertTrue(Costs.known("claude-sonnet-5-5"));
        assertTrue(Costs.known("claude-opus-5-5"));
    }
}
