package de.art.jarvis;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MetaTest {
    @Test
    public void metaVonHeuteIstAktuell() {
        assertTrue(Meta.isFresh("2026-11-02", "2026-11-02"));
        assertFalse(Meta.isFresh("2026-11-01", "2026-11-02"));
        assertFalse(Meta.isFresh("", "2026-11-02"));
        assertFalse(Meta.isFresh(null, "2026-11-02"));
    }

    @Test
    public void ohneMetaWarntJarvisVorVeraltetemWissen() {
        assertTrue(Meta.forPersona("", "").contains("veraltet"));
    }

    @Test
    public void metaHatVorrangVorAltemWissen() {
        String block = Meta.forPersona("Set 18, Patch 18.3: Top-Comp …", "2026-11-02");
        assertTrue(block.contains("2026-11-02"));
        assertTrue(block.contains("Vorrang"));
        assertTrue(block.contains("Patch 18.3"));
    }

    /** Wunsch #23: nur geprüfte Meta speichern. */
    @Test
    public void nurMetaMitSetUndPatchZaehlt() {
        String rest = "\n- Comp A: Kern … Carry … Gegenstände …".repeat(6);
        assertTrue(Meta.isPlausible("Set 17 (Name), Patch 17.4, Stand 2026-11-07" + rest));
        assertTrue(Meta.isPlausible("Set 16, Patch 25.S1.1, Stand 2026-11-07" + rest)); // Riots Jahresschema
        assertTrue(Meta.isPlausible("SET 9.5 · patch 14.2b" + rest));
        assertFalse(Meta.isPlausible("Set 17, Stand unbekannt" + rest));          // kein Patch
        assertFalse(Meta.isPlausible("Patch 17.4, aktuelle Comps" + rest));       // kein Set
        assertFalse(Meta.isPlausible("Set 17, Patch 17.4"));                       // zu dünn
        assertFalse(Meta.isPlausible(null));
        assertTrue(Meta.prompt("2026-11-08").contains("ERSTE Zeile"));
    }

    @Test
    public void rechercheVerlangtAktuellenPatchUndDeutscheNamen() {
        String p = Meta.prompt("2026-11-02");
        assertTrue(p.contains("2026-11-02"));
        assertTrue(p.contains("deutschen Namen"));
    }
}
