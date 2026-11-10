package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Kosten pro Anfrage – Preise laut Anthropic-Preisliste vom 8. Nov. 2026. */
public class CostsTest {

    @Test
    public void openAICacheNichtDoppeltBerechnen() {
        assertTrue(Costs.known("gpt-6-luna"));
        assertEquals(15 * 0.10e-6 + 5 * 0.01e-6 + 7 * 0.50e-6,
                Costs.usd("gpt-6-luna", 15, 0, 5, 7, 0), 1e-12);
    }

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
        assertFalse(Costs.known("unbekanntes-modell"));
        assertEquals(0, Costs.usd("unbekanntes-modell", 1000, 0, 0, 100, 0), 0);
        assertTrue(Costs.known("claude-sonnet-5-5"));
        assertTrue(Costs.known("claude-opus-5-5"));
    }

    @Test
    public void aufschluesselungGroessterPostenZuerstOhneNullen() {
        // Reihenfolge wie Costs.Kind: Fragen, Filter, Wächter, Recherche.
        double[] usd = {3.12, 0.0004, 0.041, 0};
        assertEquals("Fragen 312 ct · Wächter 4,1 ct · Filter 0,0 ct", Costs.breakdown(usd));
        assertEquals("", Costs.breakdown(new double[4]));
        assertEquals("", Costs.breakdown(null));
        // Kürzere Liste (ältere Speicherstände) bricht nicht.
        assertEquals("Fragen 50 ct", Costs.breakdown(new double[] {0.5}));
    }

    @Test
    public void tagesgrenze() {
        assertFalse(Costs.overLimit(4.99, 500));
        assertTrue(Costs.overLimit(5.0, 500));
        assertTrue(Costs.overLimit(8.75, 500));
        // 0 = keine Grenze, auch an einem teuren Tag.
        assertFalse(Costs.overLimit(100, 0));
        assertFalse(Costs.overLimit(0, 500));
    }
}
