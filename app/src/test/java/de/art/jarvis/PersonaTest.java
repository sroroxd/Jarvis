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

    /** Wunsch #47 „Wachwort entfernen“: Beim Dauerzuhören bleibt Gerede, das nicht Jarvis gilt, unbeantwortet. */
    @Test
    public void dauerzuhoerenIgnoriertFremdesGerede() {
        String offen = Persona.turnContext(false, false, true, at(20, 0));
        assertTrue(offen.contains("durchgehend zu, ohne Wachwort"));
        assertTrue(offen.contains("[STILL]"));
        assertFalse(Persona.turnContext(false, false, false, at(20, 0)).contains("[STILL]"));
        assertFalse(Persona.turnContext(false, false, at(20, 0)).contains("[STILL]"));
        assertTrue(Persona.system(false, "", "", "", true, false).contains("Ein Wachwort gibt es nicht mehr"));
        assertTrue(ProactiveCoach.isStill("[STILL]")); // derselbe Weg wie beim proaktiven Coaching: Jarvis bleibt stumm
    }

    /** Arts Plan vom 30. Okt.: Spielplan festlegen und halten, „Plan?“ und „Warum?“, dritter Satz nur bei Schlüsselmomenten. */
    @Test
    public void spielplanWirdGehaltenUndMitgefuehrt() {
        String s = Persona.system(true, "", "", "", false, false);
        assertTrue(s.contains("SPIELPLAN"));
        assertTrue(s.contains("Planwechsel"));
        assertTrue(s.contains("„Plan?“"));
        assertTrue(s.contains("„Warum?“"));
        assertTrue(s.contains("| Plan …]"));          // der Plan steht in der stillen Ablese-Zeile und bleibt im Verlauf
        assertTrue(s.contains("dritten kurzen Satz"));
        assertTrue(s.contains("was gerade Platzierung kostet"));
        assertFalse(Persona.system(false, "", "", "", true, false).contains("SPIELPLAN"));
        // Die Ablese-Zeile mit Plan wird weiter erkannt und nie vorgelesen.
        String antwort = "[STAND: Phase 3-2 | Gold 32 | Stufe 6 | Leben 54 | Plan Ahnenholz, Carry Xayah] Kaufen Sie Ornn.";
        assertEquals("Phase 3-2 | Gold 32 | Stufe 6 | Leben 54 | Plan Ahnenholz, Carry Xayah", GameReadout.find(antwort));
        assertEquals("Kaufen Sie Ornn.", GameReadout.strip(antwort));
    }

    @Test
    public void spielmodusOhneBildSagtDasZuerst() {
        String s = Persona.turnContext(true, false, at(20, 0));
        assertTrue(s.contains("Spielfeld gerade NICHT"));
        assertTrue(s.contains("Gesamter Bildschirm"));
    }
}
