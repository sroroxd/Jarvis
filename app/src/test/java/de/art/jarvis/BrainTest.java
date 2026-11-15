package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Arts Vorgabe: keine kostenpflichtigen Aufrufe ohne ausdrückliche Zustimmung. */
public class BrainTest {

    @Test
    public void ohneSchluesselOfflineTest() {
        assertEquals(Brain.OFFLINE_TEST, Brain.choose(Brain.PROVIDER_FREE, "", ""));
        assertEquals(Brain.OFFLINE_TEST, Brain.choose(null, null, null));
    }

    @Test
    public void geminiSchluesselGibtGemini() {
        assertEquals(Brain.GEMINI, Brain.choose(Brain.PROVIDER_FREE, "", "AIzaTest"));
    }

    /** Ein Claude-Schlüssel allein (z. B. aus einer wiederhergestellten Sicherung) kostet nie Geld. */
    @Test
    public void claudeSchluesselAlleinIstNichtBezahlt() {
        assertEquals(Brain.OFFLINE_TEST, Brain.choose(Brain.PROVIDER_FREE, "sk-ant-x", ""));
        assertEquals(Brain.GEMINI, Brain.choose(Brain.PROVIDER_FREE, "sk-ant-x", "AIzaTest"));
        assertFalse(Brain.choose(Brain.PROVIDER_FREE, "sk-ant-x", "").paid());
    }

    @Test
    public void claudeNurMitWahlUndSchluessel() {
        assertEquals(Brain.CLAUDE, Brain.choose(Brain.PROVIDER_CLAUDE, "sk-ant-x", ""));
        assertTrue(Brain.CLAUDE.paid());
        // Gewählt, aber kein Schlüssel: kostenlos weiter statt Fehler.
        assertEquals(Brain.GEMINI, Brain.choose(Brain.PROVIDER_CLAUDE, " ", "AIzaTest"));
        assertEquals(Brain.OFFLINE_TEST, Brain.choose(Brain.PROVIDER_CLAUDE, "", ""));
    }

    @Test
    public void nurClaudeIstBezahlt() {
        assertFalse(Brain.OFFLINE_TEST.paid());
        assertFalse(Brain.GEMINI.paid());
    }
}
