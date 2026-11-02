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

    @Test
    public void rechercheVerlangtAktuellenPatchUndDeutscheNamen() {
        String p = Meta.prompt("2026-11-02");
        assertTrue(p.contains("2026-11-02"));
        assertTrue(p.contains("deutschen Namen"));
    }
}
