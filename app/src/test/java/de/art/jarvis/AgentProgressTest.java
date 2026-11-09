package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Zwischenansagen für lange Agenten-Aufträge. */
public class AgentProgressTest {

    @Test
    public void faelligErstNachDemAbstand() {
        long start = 10_000;
        assertFalse(AgentProgress.faellig(start + AgentProgress.ABSTAND_MS - 1, start));
        assertTrue(AgentProgress.faellig(start + AgentProgress.ABSTAND_MS, start));
        assertTrue(AgentProgress.faellig(start + AgentProgress.ABSTAND_MS * 2, start));
    }

    @Test
    public void ansageMitTaetigkeit() {
        // Tätigkeiten kommen in Ich-Form, ohne Satzzeichen – es wird ein Punkt ergänzt.
        assertEquals("Moment noch, Sir – führe gerade einen Befehl aus.",
                AgentProgress.ansage("führe gerade einen Befehl aus"));
        assertEquals("Moment noch, Sir – sehe mir Dateien an.",
                AgentProgress.ansage("sehe mir Dateien an"));
    }

    @Test
    public void ansageMitFertigemSatz() {
        // Kommt schon ein ganzer Satz (aus Claudes Zwischentext), bleibt das Satzende erhalten.
        assertEquals("Moment noch, Sir – Lade die Daten herunter, das dauert einen Moment.",
                AgentProgress.ansage("Lade die Daten herunter, das dauert einen Moment."));
    }

    @Test
    public void ansageOhneStand() {
        assertEquals("Ich bin noch dran, Sir.", AgentProgress.ansage(""));
        assertEquals("Ich bin noch dran, Sir.", AgentProgress.ansage(null));
        assertEquals("Ich bin noch dran, Sir.", AgentProgress.ansage("   "));
    }
}
