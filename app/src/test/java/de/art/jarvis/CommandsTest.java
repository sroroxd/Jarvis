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
}
