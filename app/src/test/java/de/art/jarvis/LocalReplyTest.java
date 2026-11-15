package de.art.jarvis;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Offline-Testantwort: lang genug zum Unterbrechen, Folgefragen beweisen den Gesprächsverlauf. */
public class LocalReplyTest {

    @Test
    public void ersteFrageWirdWiederholt() {
        String a = LocalReply.answer("Wie spät ist es?", null, 1);
        assertTrue(a.startsWith(LocalReply.MARK));
        assertTrue(a.contains("Ich habe verstanden: Wie spät ist es."));
        assertFalse(a.contains(LocalReply.FOLLOW_MARK));
    }

    @Test
    public void folgefrageNenntDieVorigeFrage() {
        String a = LocalReply.answer("Und morgen?", "Wie wird das Wetter heute?", 2);
        assertTrue(a.contains(LocalReply.FOLLOW_MARK + ": Wie wird das Wetter heute."));
        assertTrue(a.contains("Jetzt fragen Sie: Und morgen."));
        assertTrue(a.contains("Folgefrage 2"));
    }

    /** Genug Sätze, damit Art mittendrin „Stopp“ sagen kann – und satzweise gestreamt wird. */
    @Test
    public void mehrereSaetzeZumUnterbrechen() {
        SpeechChunker c = new SpeechChunker();
        List<String> sentences = c.feed(LocalReply.answer("Test", null, 1));
        String rest = c.finish();
        int n = sentences.size() + (rest.isEmpty() ? 0 : 1);
        assertTrue("nur " + n + " Sätze", n >= 4);
    }

    @Test
    public void leereVorigeFrageZaehltAlsErste() {
        assertFalse(LocalReply.answer("Hallo", "  ", 1).contains(LocalReply.FOLLOW_MARK));
    }
}
