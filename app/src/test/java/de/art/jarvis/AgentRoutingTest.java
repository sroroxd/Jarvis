package de.art.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

public class AgentRoutingTest {
    @Test public void agentErlaubtStartOhneApiSchluesselAberNichtImSpielmodus() {
        assertTrue(AgentRouting.canStart(false, true, false));
        assertFalse(AgentRouting.canStart(false, true, true));
        assertFalse(AgentRouting.canStart(false, false, false));
        assertTrue(AgentRouting.canStart(true, false, true));
    }
    @Test public void alltagsfrageGehtOhneApiAnDenAgenten() {
        assertEquals("Wie geht es dir?", AgentRouting.task("Wie geht es dir?", false, true, false));
        assertNull(AgentRouting.task("Wie geht es dir?", true, true, false));
        assertNull(AgentRouting.task("Wie geht es dir?", false, false, false));
        assertNull(AgentRouting.task("Wie geht es dir?", false, true, true));
    }
    @Test public void ausdruecklicherAuftragGehtAuchMitApiAnDenAgenten() {
        assertEquals("Plane morgen", AgentRouting.task("Übernimm das: Plane morgen", true, true, false));
    }
    @Test public void kaltesOffenesMikrofonStartetKeineBeilaeufigenAgentauftraege() {
        assertFalse(AgentRouting.addressed("Was läuft im Fernsehen?", false, true, 120000, 0));
        assertTrue(AgentRouting.addressed("Jarvis, wie geht es dir?", false, true, 120000, 0));
        assertTrue(AgentRouting.addressed("Übernimm das: Plane morgen", false, true, 120000, 0));
    }
    @Test public void antippenUndWarmeFolgefrageBrauchenKeinenNamen() {
        assertTrue(AgentRouting.addressed("Wie geht es dir?", false, false, 120000, 0));
        assertTrue(AgentRouting.addressed("Und morgen?", false, true, 120000, 119000));
    }
}
