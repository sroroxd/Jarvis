package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Die Grundanweisung muss von Frage zu Frage gleich bleiben, sonst greift der Zwischenspeicher (Prompt-Caching)
 * nie. Uhrzeit und Bildschirm-Hinweis gehören deshalb in turnContext().
 */
public class PersonaTest {

    private static Date at(int hour, int minute) {
        return new GregorianCalendar(2026, Calendar.NOVEMBER, 7, hour, minute).getTime();
    }

    @Test
    public void grundanweisungEnthaeltKeineUhrzeit() {
        String s = Persona.system(false, "Art mag kurze Antworten.", "", "", true, false);
        assertFalse(s.contains("Uhr,"));
        assertFalse(s.contains("Aktuelle Zeit:"));
        assertTrue(s.contains("[KONTEXT:"));
    }

    @Test
    public void grundanweisungIstBeiJederFrageGleich() {
        String a = Persona.system(true, "", "Meta-Text", "2026-11-07", false, false);
        String b = Persona.system(true, "", "Meta-Text", "2026-11-07", false, false);
        assertEquals(a, b);
    }

    @Test
    public void kontextNenntZeitUndBildschirmfoto() {
        String mit = Persona.turnContext(false, true, at(21, 50));
        assertTrue(mit.startsWith("[KONTEXT: Aktuelle Zeit: Samstag, 7. November 2026, 21:50 Uhr."));
        assertTrue(mit.contains("Angehängt ist ein Bildschirmfoto"));
        assertTrue(mit.endsWith("]"));

        String ohne = Persona.turnContext(false, false, at(9, 5));
        assertTrue(ohne.contains("09:05 Uhr"));
        assertTrue(ohne.contains("kein Bildschirmfoto"));
    }

    /** Wunsch #21: im Spielmodus kurze Befehle statt Erklärungen. */
    @Test
    public void spielmodusVerlangtKurzeAntworten() {
        String s = Persona.system(true, "", "", "", false, false);
        assertTrue(s.contains("höchstens zwanzig Wörter"));
        assertTrue(s.contains("Keine Begründung"));
        assertFalse(s.contains("Empfiehl Art einmalig"));
        assertFalse(Persona.system(false, "", "", "", true, false).contains("zwanzig Wörter"));
    }

    @Test
    public void spielmodusOhneBildSagtDasZuerst() {
        String s = Persona.turnContext(true, false, at(20, 0));
        assertTrue(s.contains("Spielfeld gerade NICHT"));
        assertTrue(s.contains("Gesamter Bildschirm"));
    }
}
