package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Prüft die Sprachbefehle der App: Spielmodus umschalten und neue Partie. */
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

    @Test
    public void neuePartieWirdErkannt() {
        assertTrue(Commands.newGame("Neue Partie"));
        assertTrue(Commands.newGame("Jarvis, neues Spiel."));
        assertTrue(Commands.newGame("Hey Jarvis neue Partie bitte"));
        assertTrue(Commands.newGame("Nächste Partie"));
        assertTrue(Commands.newGame("Okay, neues Spiel startet"));
        assertTrue(Commands.newGame("Eine neue Partie beginnt"));
    }

    @Test
    public void neueRundeUndFragenSindKeineNeuePartie() {
        // „Runde“ gibt es in jeder Partie – das darf den Verlauf nicht löschen.
        assertFalse(Commands.newGame("Neue Runde"));
        assertFalse(Commands.newGame("Was soll ich in der neuen Partie spielen?"));
        assertFalse(Commands.newGame("Welche Comp ist für ein neues Spiel am besten geeignet"));
        assertFalse(Commands.newGame("Spielmodus an"));
        assertFalse(Commands.newGame(""));
        assertFalse(Commands.newGame(null));
        // Und umgekehrt löst „neue Partie“ nicht den Spielmodus-Schalter aus.
        assertNull(Commands.gameMode("Neue Partie"));
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

    // ------------------------------------------------- Gesprächsmodus

    @Test
    public void gespraechsmodusStarten() {
        assertEquals(Boolean.TRUE, Commands.conversationMode("Gesprächsmodus an"));
        assertEquals(Boolean.TRUE, Commands.conversationMode("Jarvis, Gesprächsmodus starten."));
        assertEquals(Boolean.TRUE, Commands.conversationMode("Hey Jarvis aktiviere den Gesprächsmodus"));
        assertEquals(Boolean.TRUE, Commands.conversationMode("Lass uns reden"));
        assertEquals(Boolean.TRUE, Commands.conversationMode("Jarvis, lass uns mal quatschen"));
    }

    @Test
    public void gespraechsmodusBeenden() {
        assertEquals(Boolean.FALSE, Commands.conversationMode("Gesprächsmodus aus"));
        assertEquals(Boolean.FALSE, Commands.conversationMode("Gesprächsmodus beenden"));
        assertEquals(Boolean.FALSE, Commands.conversationMode("Gespräch beenden"));
        assertEquals(Boolean.FALSE, Commands.conversationMode("Okay danke, das war's"));
    }

    @Test
    public void fragenUeberDenGespraechsmodusSchaltenNichts() {
        assertNull(Commands.conversationMode("Was kostet der Gesprächsmodus eigentlich pro Stunde?"));
        assertNull(Commands.conversationMode("Gesprächsmodus an oder aus"));
        assertNull(Commands.conversationMode("Spielmodus an"));
        assertNull(Commands.conversationMode("Wie wird das Wetter morgen?"));
        assertNull(Commands.conversationMode(""));
        assertNull(Commands.conversationMode(null));
        // und umgekehrt schaltet der Gesprächsmodus nicht den Spielmodus
        assertNull(Commands.gameMode("Gesprächsmodus an"));
    }

    @Test
    public void schlussbefehlAmEndeEinesSatzes() {
        assertTrue(Commands.endsConversation("Okay, das reicht für heute, Gesprächsmodus aus"));
        assertTrue(Commands.endsConversation("Danke dir. Gespräch beenden."));
        assertTrue(Commands.endsConversation("Gesprächsmodus beenden"));
        assertFalse(Commands.endsConversation("Wie beende ich eigentlich den Gesprächsmodus?"));
        assertFalse(Commands.endsConversation("Soll ich das Item kaufen oder aus der Bank nehmen"));
        assertFalse(Commands.endsConversation(""));
        assertFalse(Commands.endsConversation(null));
    }
}
