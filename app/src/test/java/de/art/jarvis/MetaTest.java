package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    /** 8. Nov.: Vor der Zusammenfassung stand englisches Vorgeplänkel – die Einstellungen zeigten es als „Stand“. */
    @Test
    public void vorgeplaenkelWirdAbgeschnitten() {
        String body = "1. Comp A: Einheit X als Carry, Gegenstände Y und Z.\n2. Comp B: Einheit V, Gegenstände W.\n"
                + "3. Augment Q ist stark, Mechanik R wurde abgeschwächt. Weitere Stichpunkte zur Länge der Recherche.";
        String raw = "I have enough to answer for the current state. I'll note the limitation.\n"
                + "Set 17 (Name), Patch 17.4, Stand 2026-11-07\n" + body;
        String meta = Meta.extract(raw);
        assertTrue(meta.startsWith("Set 17 (Name), Patch 17.4"));
        assertFalse(meta.contains("I have enough"));
        assertTrue(Meta.isPlausible(raw));
        assertEquals(meta, Meta.clean(raw));
        assertTrue(Meta.forPersona(raw, "2026-11-08").contains("Set 17 (Name)"));
        assertFalse(Meta.forPersona(raw, "2026-11-08").contains("I have enough"));
    }

    @Test
    public void ohneKopfzeileNichtPlausibel() {
        // Set und Patch stehen zwar im Text, aber nie zusammen in einer Zeile wie verlangt.
        StringBuilder filler = new StringBuilder();
        for (int i = 0; i < 25; i++) filler.append("Stichpunkt ");
        String raw = "Set 17 ist aktuell.\n" + filler + "\nPatch 17.4 brachte Änderungen.";
        assertNull(Meta.extract(raw));
        assertFalse(Meta.isPlausible(raw));
        assertEquals(raw, Meta.clean(raw)); // alte Einträge ohne Kopfzeile bleiben lesbar
        assertNull(Meta.extract(null));
    }
}
