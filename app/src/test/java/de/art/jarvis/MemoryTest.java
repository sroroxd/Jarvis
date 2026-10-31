package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Prüft „Merk dir …“, „Vergiss …“ und die [MERKEN:]-Zeilen der KI. */
public class MemoryTest {

    @Test
    public void merkDirSpeichertFakt() {
        Memory.Outcome o = Memory.command("Merk dir, dass ich am liebsten Vanguard spiele.", "");
        assertNotNull(o);
        assertEquals("Gemerkt, Sir.", o.reply);
        assertEquals("- Ich am liebsten Vanguard spiele", o.memory);
    }

    @Test
    public void merkDirVarianten() {
        assertNotNull(Memory.command("merke dir bitte: Antworte immer kurz", ""));
        assertNotNull(Memory.command("Jarvis, merk dir mein Hund heißt Rex", ""));
        assertNotNull(Memory.command("Hey Jarvis merkt dir folgendes: ich spiele auf EUW", ""));
    }

    @Test
    public void doppelterFaktWirdNichtDoppeltGespeichert() {
        Memory.Outcome a = Memory.command("Merk dir Antworte kurz", "");
        Memory.Outcome b = Memory.command("Merk dir Antworte kurz", a.memory);
        assertEquals(a.memory, b.memory);
    }

    @Test
    public void vergissLoeschtPassendenFakt() {
        String mem = "- Antworte immer kurz\n- Mein Hund heißt Rex\n- Ich spiele Vanguard";
        Memory.Outcome o = Memory.command("Vergiss das mit Vanguard", mem);
        assertNotNull(o);
        assertTrue(o.reply.startsWith("Vergessen:"));
        assertFalse(o.memory.contains("Vanguard"));
        assertTrue(o.memory.contains("Rex"));
    }

    @Test
    public void vergissNichtIstKeinLoeschbefehl() {
        assertNull(Memory.command("Vergiss nicht, morgen den Müll rauszubringen", "- Müll am Dienstag"));
    }

    @Test
    public void vergissOhneStichwortFragtNach() {
        Memory.Outcome o = Memory.command("Vergiss es", "- Antworte kurz");
        assertNotNull(o);
        assertEquals("- Antworte kurz", o.memory);
    }

    @Test
    public void normaleFrageIstKeinBefehl() {
        assertNull(Memory.command("Wie spät ist es?", ""));
        assertNull(Memory.command("Was siehst du auf meinem Bildschirm?", ""));
    }

    @Test
    public void merkenZeileDerKiWirdGespeichertUndEntfernt() {
        Memory.Outcome o = Memory.tags("Alles klar, Sir.\n[MERKEN: Art spielt auf EUW]", "");
        assertEquals("Alles klar, Sir.", o.reply);
        assertEquals("- Art spielt auf EUW", o.memory);
    }

    @Test
    public void ohneTagsBleibtAllesGleich() {
        Memory.Outcome o = Memory.tags("Es ist zwölf Uhr.", "- Antworte kurz");
        assertEquals("Es ist zwölf Uhr.", o.reply);
        assertEquals("- Antworte kurz", o.memory);
    }

    @Test
    public void gedaechtnisIstBegrenzt() {
        StringBuilder mem = new StringBuilder();
        for (int i = 0; i < Memory.MAX_FACTS + 5; i++) mem.append("- Fakt ").append(i).append('\n');
        Memory.Outcome o = Memory.command("Merk dir neuer Fakt", mem.toString());
        assertEquals(Memory.MAX_FACTS, Memory.load(o.memory).size());
        assertTrue(o.memory.endsWith("Neuer Fakt"));
    }
}
