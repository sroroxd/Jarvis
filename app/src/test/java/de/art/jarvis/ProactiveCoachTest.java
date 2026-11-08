package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Wunsch #41/#42: einmal pro Planungsphase von selbst ansagen – nicht öfter, nicht im Kampf. */
public class ProactiveCoachTest {
    private static ProactiveCoach.Phase p(String reply) {
        return ProactiveCoach.parse(reply);
    }

    @Test
    public void antwortDesWaechtersWirdGelesen() {
        assertEquals(ProactiveCoach.Kind.PLANNING, p("PLANUNG 3-2").kind);
        assertEquals("3-2", p("PLANUNG 3-2").stage);
        assertEquals("4-1", p("planung 4 – 1").stage);
        assertNull(p("PLANUNG ?").stage);
        assertEquals(ProactiveCoach.Kind.COMBAT, p("KAMPF").kind);
        assertEquals(ProactiveCoach.Kind.OTHER, p("ANDERES").kind);
        assertEquals(ProactiveCoach.Kind.OTHER, p("Ich bin mir nicht sicher.").kind);
        assertEquals(ProactiveCoach.Kind.OTHER, p(null).kind);
        assertEquals("2-1", p("**PLANUNG 2-1**").stage);             // Formatierung des Modells
        assertEquals(ProactiveCoach.Kind.COMBAT, p("- KAMPF").kind);
    }

    @Test
    public void einmalProPlanungsphase() {
        ProactiveCoach c = new ProactiveCoach();
        long t = 1_000_000;
        assertTrue(c.due(p("PLANUNG 3-1"), t));                 // erste Planungsphase
        c.coached(p("PLANUNG 3-1"), t);
        assertFalse(c.due(p("PLANUNG 3-1"), t + 6_000));        // dieselbe Phase: schweigen
        assertFalse(c.due(p("PLANUNG 3-1"), t + 30_000));       // auch nach der Mindestpause
        assertFalse(c.due(p("KAMPF"), t + 36_000));             // im Kampf nie
        assertTrue(c.due(p("PLANUNG 3-2"), t + 70_000));        // nächste Runde
    }

    /** Hält der Wächter ein Kampfbild einmal für Planung, darf Jarvis nicht mitten im Kampf reden. */
    @Test
    public void fehlgriffImKampfBleibtStill() {
        ProactiveCoach c = new ProactiveCoach();
        long t = 1_000_000;
        c.coached(p("PLANUNG 1-3"), t);
        assertFalse(c.due(p("KAMPF"), t + 30_000));
        assertFalse(c.due(p("PLANUNG 1-3"), t + 36_000));       // Fehlgriff: dieselbe Runde, also Kampf – schweigen
        assertFalse(c.due(p("KAMPF"), t + 42_000));
        assertTrue(c.due(p("PLANUNG 1-4"), t + 60_000));        // die nächste Runde schon
    }

    @Test
    public void nichtZuSchnellHintereinander() {
        ProactiveCoach c = new ProactiveCoach();
        long t = 1_000_000;
        c.coached(p("PLANUNG 2-1"), t);
        c.due(p("KAMPF"), t + 5_000);
        assertFalse(c.due(p("PLANUNG 2-2"), t + 20_000));       // Wächter irrt sich kurz – unter 25 s nichts
        assertTrue(c.due(p("PLANUNG 2-2"), t + 26_000));
    }

    @Test
    public void verpassterKampfAberNeuePhase() {
        ProactiveCoach c = new ProactiveCoach();
        long t = 1_000_000;
        c.coached(p("PLANUNG 3-5"), t);
        assertTrue(c.due(p("PLANUNG 3-6"), t + 60_000));        // Kampf nie gesehen, aber Phase gewechselt
        assertFalse(c.due(p("PLANUNG ?"), t + 60_000));         // unlesbar und kein Kampf dazwischen
    }

    @Test
    public void karussellZaehltAlsWechsel() {
        ProactiveCoach c = new ProactiveCoach();
        long t = 1_000_000;
        c.coached(p("PLANUNG ?"), t);
        c.due(p("ANDERES"), t + 30_000);                         // Karussell
        assertTrue(c.due(p("PLANUNG ?"), t + 50_000));
    }

    @Test
    public void eigeneFrageErsetztDieAnsage() {
        ProactiveCoach c = new ProactiveCoach();
        long t = 1_000_000;
        assertTrue(c.due(p("PLANUNG 4-2"), t));                 // Art kommt der Ansage zuvor …
        c.userAsked(t + 3_000);
        assertFalse(c.due(p("PLANUNG 4-2"), t + 30_000));       // … also keine Wiederholung in dieser Runde
        assertFalse(c.due(p("KAMPF"), t + 40_000));
        assertTrue(c.due(p("PLANUNG 4-3"), t + 70_000));        // nächste Runde wieder
    }

    @Test
    public void frageImKampfZaehltNicht() {
        ProactiveCoach c = new ProactiveCoach();
        long t = 1_000_000;
        c.due(p("KAMPF"), t);
        c.userAsked(t + 2_000);                                  // Frage mitten im Kampf
        assertTrue(c.due(p("PLANUNG 2-5"), t + 20_000));        // die Planungsphase danach bekommt ihre Ansage
    }

    /** Gerätetest Build 84: Der Wächter hielt Arts Startbild einmal für „PLANUNG 1-1“. Runde 1-1 ist nie Planung. */
    @Test
    public void startrundeIstNiePlanung() {
        assertEquals(ProactiveCoach.Kind.OTHER, p("PLANUNG 1-1").kind);
        assertEquals("ANDERES", ProactiveCoach.label(p("PLANUNG 1-1")));
        assertEquals("PLANUNG 1-2", ProactiveCoach.label(p("PLANUNG 1-2")));
        assertEquals("PLANUNG ?", ProactiveCoach.label(p("PLANUNG ?")));
        assertEquals("KAMPF", ProactiveCoach.label(p("KAMPF")));
        ProactiveCoach c = new ProactiveCoach();
        assertFalse(c.due(p("PLANUNG 1-1"), 1_000_000));          // keine Ansage zum Start
        assertTrue(c.due(p("PLANUNG 1-2"), 1_030_000));           // die erste echte Planungsphase schon
    }

    @Test
    public void schweigenWirdErkannt() {
        assertTrue(ProactiveCoach.isStill("[STILL]"));
        assertTrue(ProactiveCoach.isStill("  [ still ]\n"));
        assertFalse(ProactiveCoach.isStill("Kaufen Sie Ahri. [STILL]"));
        assertFalse(ProactiveCoach.isStill("Kaufen Sie Ahri."));
        assertFalse(ProactiveCoach.isStill(null));
        assertEquals("[AUTO] Neue Planungsphase 3-2.", ProactiveCoach.autoMessage(p("PLANUNG 3-2")));
        assertEquals("[AUTO] Neue Planungsphase.", ProactiveCoach.autoMessage(p("PLANUNG ?")));
    }
}
