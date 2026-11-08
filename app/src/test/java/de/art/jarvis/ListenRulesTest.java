package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** „Immer zuhören“ absichern und Kostenbremse (Gesamtanalyse 8. Nov., Punkt 1). */
public class ListenRulesTest {

    /** Vorher: nach fünf Fehlern aufgegeben und nie wieder angeworfen. Jetzt: weiter versuchen. */
    @Test
    public void nachFehlernNichtAufgeben() {
        assertEquals(1000, ListenRules.retryDelay(1, true));
        assertEquals(8000, ListenRules.retryDelay(5, true));
        assertEquals(ListenRules.SLOW_RETRY_MS, ListenRules.retryDelay(6, true));
        assertEquals(ListenRules.SLOW_RETRY_MS, ListenRules.retryDelay(100, true)); // kein Überlauf, kein Aufgeben
        assertEquals(-1, ListenRules.retryDelay(6, false));                          // manuelles Dauergespräch wie bisher
    }

    @Test
    public void waechterWirftNurAnWennNoetig() {
        assertTrue(ListenRules.kickNeeded(true, false, true, false));   // der Fehler: soll zuhören, tut es nicht
        assertTrue(ListenRules.kickNeeded(true, true, true, false));    // Dauergespräch an, aber kein Neustart geplant
        assertFalse(ListenRules.kickNeeded(true, true, true, true));    // Neustart steht schon an
        assertFalse(ListenRules.kickNeeded(true, true, false, false));  // hört, denkt oder spricht gerade
        assertFalse(ListenRules.kickNeeded(false, false, true, false)); // Art hat pausiert / Bildschirm aus
    }

    @Test
    public void filterNurNachGespraechspause() {
        long now = 1_000_000;
        assertTrue(ListenRules.needsGate(true, now, now - 90_000, "Was soll ich kaufen?"));
        assertFalse(ListenRules.needsGate(true, now, now - 40_000, "Und dann?"));             // Folgefrage: schnell
        assertFalse(ListenRules.needsGate(true, now, now - 90_000, "Jarvis, was soll ich kaufen?")); // beim Namen
        assertFalse(ListenRules.needsGate(true, now, now - 90_000, "jarvis wie spät ist es"));
        assertFalse(ListenRules.needsGate(false, now, now - 90_000, "Was soll ich kaufen?")); // angetippt
    }

    @Test
    public void antwortDesFilters() {
        assertFalse(ListenRules.addressed("NEIN"));
        assertFalse(ListenRules.addressed("nein."));
        assertFalse(ListenRules.addressed("**NEIN**"));
        assertTrue(ListenRules.addressed("JA"));
        assertTrue(ListenRules.addressed("Ja, das ist eine Frage."));
        assertTrue(ListenRules.addressed("Unklar"));  // im Zweifel antworten
        assertTrue(ListenRules.addressed(null));
    }

    @Test
    public void frageAnDenFilter() {
        String p = ListenRules.gatePrompt("Wie viel kostet das?", "Kaufen Sie Ornn.", 45, true);
        assertTrue(p.contains("„Wie viel kostet das?“"));
        assertTrue(p.contains("vor 45 Sekunden"));
        assertTrue(p.contains("Teamfight Tactics"));
        assertTrue(p.contains("Im Zweifel JA"));
        assertFalse(ListenRules.gatePrompt("Hallo", "", 0, false).contains("zuletzt"));
    }
}
