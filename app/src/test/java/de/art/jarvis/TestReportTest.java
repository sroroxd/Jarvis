package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Testbericht vom Handy: begrenzt, gekürzt, in Reihenfolge. */
public class TestReportTest {

    @Test
    public void zeilenInReihenfolgeMitKopf() {
        TestReport r = new TestReport();
        r.add(0, "gehört: Wie spät ist es");
        r.add(0, "dazwischenreden: unterbrochen");
        String t = r.text("Gerät: S24 Ultra");
        assertTrue(t.startsWith("JARVIS-TESTBERICHT\nGerät: S24 Ultra\n---\n"));
        assertTrue(t.indexOf("gehört") < t.indexOf("dazwischenreden"));
    }

    @Test
    public void altesFaelltHerausNeuesBleibt() {
        TestReport r = new TestReport();
        for (int i = 0; i < TestReport.MAX_LINES + 50; i++) r.add(0, "zeile " + i);
        assertEquals(TestReport.MAX_LINES, r.size());
        String t = r.text(null);
        assertFalse(t.contains(" zeile 0\n"));
        assertTrue(t.contains(" zeile " + (TestReport.MAX_LINES + 49) + "\n"));
    }

    @Test
    public void langeZeilenWerdenGekuerztUndEinzeilig() {
        TestReport r = new TestReport();
        StringBuilder lang = new StringBuilder("antwort:\n");
        for (int i = 0; i < 100; i++) lang.append("Satz. ");
        r.add(0, lang.toString());
        String t = r.text("");
        String line = t.split("---\n")[1].trim();
        assertFalse(line.contains("\n"));
        assertTrue(line.endsWith("…"));
        assertTrue(line.length() < TestReport.MAX_LINE + 20);
    }

    @Test
    public void leererBerichtSagtWasZuTunIst() {
        assertTrue(new TestReport().text("").contains("noch keine Ereignisse"));
    }
}
