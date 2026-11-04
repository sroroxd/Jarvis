package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Prüft die Sprachbefehle zum Umschalten des Spielmodus. */
public class CommandsTest {

    @Test
    public void spielmodusEinschalten() {
        assertEquals(Boolean.TRUE, Commands.gameMode("Spielmodus an"));
        assertEquals(Boolean.TRUE, Commands.gameMode("Schalte den Spielmodus ein"));
        assertEquals(Boolean.TRUE, Commands.gameMode("Aktiviere den Spielmodus."));
        assertEquals(Boolean.TRUE, Commands.gameMode("TFT-Modus starten"));
        assertEquals(Boolean.TRUE, Commands.gameMode("Hey Jarvis, Spielmodus an bitte"));
        assertEquals(Boolean.TRUE, Commands.gameMode("Spiele Modus an"));
    }

    @Test
    public void spielmodusAusschalten() {
        assertEquals(Boolean.FALSE, Commands.gameMode("Spielmodus aus"));
        assertEquals(Boolean.FALSE, Commands.gameMode("Beende den Spielmodus"));
        assertEquals(Boolean.FALSE, Commands.gameMode("TFT Modus deaktivieren"));
        assertEquals(Boolean.FALSE, Commands.gameMode("Alltagsmodus"));
        assertEquals(Boolean.FALSE, Commands.gameMode("Normaler Modus"));
    }

    @Test
    public void fragenUndAnderesLoesenNichtAus() {
        assertNull(Commands.gameMode("Was bringt mir der Spielmodus eigentlich an Vorteilen in der Partie?"));
        assertNull(Commands.gameMode("Was soll ich kaufen?"));
        assertNull(Commands.gameMode("Spielmodus"));
        assertNull(Commands.gameMode("Spielmodus an oder aus"));
        assertNull(Commands.gameMode(""));
        assertNull(Commands.gameMode(null));
    }

    // ------------------------------------------------- Aufträge an den Agenten

    @Test
    public void auftragWirdErkannt() {
        assertEquals("sortier meine Downloads nach Datum",
                Commands.agentTask("Übernimm das: sortier meine Downloads nach Datum"));
        assertEquals("sortier meine Downloads",
                Commands.agentTask("Jarvis, übernimm das, sortier meine Downloads"));
        assertEquals("schreib mir eine Einkaufsliste",
                Commands.agentTask("Auftrag: schreib mir eine Einkaufsliste"));
        assertEquals("die Rechnungen vom Oktober",
                Commands.agentTask("Kümmere dich um die Rechnungen vom Oktober"));
        assertEquals("das Backup",
                Commands.agentTask("Hey Jarvis erledige das Backup"));
        assertEquals("such mir die PDF raus",
                Commands.agentTask("Agent: such mir die PDF raus"));
        assertEquals("räum den Ordner auf",
                Commands.agentTask("Arbeite daran, räum den Ordner auf"));
    }

    @Test
    public void grossUndKleinschreibungBleibtImAuftrag() {
        assertEquals("Lies die Datei Rechnung_Oktober.PDF",
                Commands.agentTask("Übernimm das: Lies die Datei Rechnung_Oktober.PDF"));
    }

    @Test
    public void keinAuftragOhneInhalt() {
        assertNull(Commands.agentTask("Übernimm das."));
        assertNull(Commands.agentTask("Auftrag"));
        assertNull(Commands.agentTask("Kümmere dich bitte darum"));
    }

    @Test
    public void normaleFragenSindKeineAuftraege() {
        assertNull(Commands.agentTask("Wie wird das Wetter morgen?"));
        assertNull(Commands.agentTask("Agenten gibt es viele"));
        assertNull(Commands.agentTask("Aufträge sind ein gutes Thema"));
        assertNull(Commands.agentTask("Was kostet ein Auftrag bei dir?"));
        assertNull(Commands.agentTask("Spielmodus an"));
        assertNull(Commands.agentTask(""));
        assertNull(Commands.agentTask(null));
    }
}
