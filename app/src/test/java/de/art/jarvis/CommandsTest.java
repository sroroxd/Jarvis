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
}
