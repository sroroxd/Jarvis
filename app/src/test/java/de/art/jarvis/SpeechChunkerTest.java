package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Satzweises Sprechen: Was die Sprachausgabe wann bekommt. */
public class SpeechChunkerTest {

    /** Füttert den Text in kleinen Stücken (wie beim Streaming) und sammelt alle Sätze samt Rest. */
    private static List<String> stream(String text, int piece) {
        SpeechChunker c = new SpeechChunker();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i += piece) {
            out.addAll(c.feed(text.substring(i, Math.min(text.length(), i + piece))));
        }
        String rest = c.finish();
        if (!rest.isEmpty()) out.add(rest);
        return out;
    }

    @Test
    public void ersterSatzKommtSofort() {
        SpeechChunker c = new SpeechChunker();
        assertTrue(c.feed("Kaufen Sie den zweiten Kämpfer").isEmpty());
        assertTrue(c.feed(".").isEmpty()); // erst sehen, was folgt
        assertEquals(Arrays.asList("Kaufen Sie den zweiten Kämpfer."), c.feed(" Danach"));
        assertEquals(Arrays.asList(), c.feed(": auf Stufe 6"));
        assertEquals("Danach: auf Stufe 6.", c.finish() + ".");
    }

    @Test
    public void satzzeichenUndStueckelungSindEgal() {
        String text = "Ja, Sir! Ist das alles? Gut. Morgen wird es 12 Grad.";
        List<String> expected = Arrays.asList("Ja, Sir!", "Ist das alles?", "Gut.", "Morgen wird es 12 Grad.");
        for (int piece : new int[]{1, 2, 3, 7, 100}) assertEquals("Stückgröße " + piece, expected, stream(text, piece));
    }

    @Test
    public void abkuerzungenUndZahlenTrennenNicht() {
        assertEquals(Arrays.asList("Nehmen Sie z. B. den Bogen, ca. 3,5 Sekunden.", "Fertig."),
                stream("Nehmen Sie z. B. den Bogen, ca. 3,5 Sekunden. Fertig.", 3));
        // Zahl am Satzanfang ist keine Nummerierung und kein Satzende.
        assertEquals(Arrays.asList("51. Soll ich weiterrechnen?"), stream("51. Soll ich weiterrechnen?", 4));
        // Zahl am Satzende schon.
        assertEquals(Arrays.asList("Gehen Sie auf Stufe 6.", "Danach rollen."), stream("Gehen Sie auf Stufe 6. Danach rollen.", 5));
    }

    @Test
    public void steuerzeilenWerdenNieVorgelesen() {
        assertEquals(Arrays.asList("Gemerkt, Sir."),
                stream("Gemerkt, Sir.\n[MERKEN: Art spielt TFT auf EUW]", 4));
        assertEquals(Arrays.asList("Dauergespräch an."),
                stream("Dauergespräch an. [DAUERGESPRÄCH: AN]", 2));
        // Nur eine Steuerzeile: nichts zu sagen.
        assertEquals(Arrays.asList(), stream("[WUNSCH: Kürzer | Antworten kürzer]", 3));
    }

    @Test
    public void zeilenUndListenWerdenSaetze() {
        assertEquals(Arrays.asList("Erstens kaufen", "Dann rollen."),
                stream("Erstens kaufen\n2. Dann rollen.", 3));
        assertEquals(Arrays.asList("Gute Idee!", "Erstens kaufen", "Zweitens leveln"),
                stream("**Gute Idee!**\n- Erstens kaufen\n- Zweitens leveln", 5));
    }

    @Test
    public void anfuehrungszeichenNachDemSatzende() {
        assertEquals(Arrays.asList("Er sagte „Ja.“", "Dann ging er."), stream("Er sagte „Ja.“ Dann ging er.", 2));
    }

    @Test
    public void ohneSatzendeKommtAllesAmSchluss() {
        assertEquals(Arrays.asList("Einhundertzwei"), stream("Einhundertzwei", 3));
        assertEquals(Arrays.asList("102"), stream("102", 1));
        assertEquals(Arrays.asList(), stream("", 1));
    }
}
