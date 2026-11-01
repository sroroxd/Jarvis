package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Prüft Textbereinigung fürs Vorlesen, Schlüssel- und Modellprüfung sowie Fehlermeldungen. */
public class TextTest {

    @Test
    public void entferntMarkdownListenUndEmojis() {
        assertEquals("Gute Idee! Erstens kaufen. Zweitens leveln. Dann rollen",
                Text.cleanForSpeech("**Gute Idee!** 😀\n- Erstens kaufen\n- Zweitens leveln\n1. Dann rollen"));
    }

    @Test
    public void normalerSatzBleibtUnveraendert() {
        assertEquals("Kauf den Kämpfer und level auf acht.", Text.cleanForSpeech("Kauf den Kämpfer und level auf acht."));
    }

    @Test
    public void schluesselOhneUnsichtbareZeichen() {
        assertEquals("sk-ant-abc123", Text.cleanKey(" sk-ant-​abc\n123 ﻿"));
    }

    @Test
    public void nurEchteModellnamen() {
        assertTrue(Text.isValidModel("claude-haiku-5-5"));
        assertTrue(Text.isValidModel("claude-sonnet-5-5"));
        assertTrue(Text.isValidModel("gemini-flash-latest"));
        assertFalse(Text.isValidModel("2"));
        assertFalse(Text.isValidModel("sonnet"));
        assertFalse(Text.isValidModel(""));
        assertFalse(Text.isValidModel(null));
    }

    @Test
    public void schluesselVorschauVerraetNichtDenGanzenSchluessel() {
        String key = "sk-ant-api03-ABCDEFGHIJKLMNOPQRSTUVWXYZ-x7Qa";
        String p = Text.keyPreview(key);
        assertTrue(p.contains("sk-ant-api"));
        assertTrue(p.contains("x7Qa"));
        assertTrue(p.contains("Claude"));
        assertFalse(p.contains("ABCDEFGHIJ"));
    }

    @Test
    public void fehler400IstKeinModellfehler() {
        // Genau dieser Fall wurde früher fälschlich als „Modell nicht verfügbar“ gemeldet.
        String m = Text.claudeError(400, "invalid_request_error", "temperature is deprecated for this model.");
        assertFalse(m.contains("Modell ist nicht verfügbar"));
        assertTrue(m.contains("temperature is deprecated"));
    }

    @Test
    public void fehlerZuordnung() {
        assertTrue(Text.claudeError(401, "authentication_error", "invalid x-api-key").contains("Schlüssel wird abgelehnt"));
        assertTrue(Text.claudeError(404, "not_found_error", "model: 2").contains("Modell ist nicht verfügbar"));
        assertTrue(Text.claudeError(400, "invalid_request_error",
                "Your credit balance is too low to access the Anthropic API.").contains("Guthaben"));
        assertTrue(Text.claudeError(529, "overloaded_error", "Overloaded").contains("überlastet"));
    }

    @Test
    public void leereAntwortKlingtNichtWieBestaetigung() {
        // Früher wurde eine leere KI-Antwort als „Erledigt, Sir.“ vorgelesen.
        assertTrue(Text.replyOrFallback("", "").contains("keine Antwort"));
        assertEquals("Notiert, Sir.", Text.replyOrFallback("[MERKEN: Art mag Kaffee]", ""));
        assertEquals("Es ist acht Uhr.", Text.replyOrFallback("Es ist acht Uhr.", "Es ist acht Uhr."));
    }

    @Test
    public void vollstaendigeFrageWirdSofortAbgeschickt() {
        assertFalse(Text.looksUnfinished("Wie spät ist es"));
        assertFalse(Text.looksUnfinished("Was siehst du auf meinem Bildschirm"));
        assertFalse(Text.looksUnfinished("Welche Items soll ich auf Jinx bauen?"));
        assertFalse(Text.looksUnfinished("Merk dir dass ich Vanguard spiele"));
        assertFalse(Text.looksUnfinished("Hörst du mich"));
        assertFalse(Text.looksUnfinished("Mach das bitte"));
        assertFalse(Text.looksUnfinished("Danke Jarvis"));
    }

    @Test
    public void denkpauseMittenImSatzWirdAbgewartet() {
        assertTrue(Text.looksUnfinished("Ich wollte fragen ob"));
        assertTrue(Text.looksUnfinished("Kannst du mir sagen wie"));
        assertTrue(Text.looksUnfinished("Was ist besser der"));
        assertTrue(Text.looksUnfinished("Jarvis"));
        assertTrue(Text.looksUnfinished("ähm"));
        assertTrue(Text.looksUnfinished(""));
    }
}
