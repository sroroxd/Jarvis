package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Wünsche an die Entwicklung: Erkennung, Steuerzeile, Speicher. */
public class WishesTest {

    @Test
    public void sprachbefehlWirdErkannt() {
        assertEquals("Jarvis soll mir die Taktikerleben vorlesen",
                Wishes.command("Notier für die Entwicklung: Jarvis soll mir die Taktikerleben vorlesen"));
        assertEquals("die Stimme etwas tiefer",
                Wishes.command("Jarvis, Wunsch für die Entwicklung, die Stimme etwas tiefer"));
        assertEquals("ein dunkleres Design",
                Wishes.command("Für die nächste Version: ein dunkleres Design"));
        assertEquals("Wecker stellen können",
                Wishes.command("Feature-Wunsch: Wecker stellen können"));
        assertEquals("er beim Zocken leiser spricht",
                Wishes.command("Notiere dir mal für die Entwicklung, dass er beim Zocken leiser spricht"));
    }

    @Test
    public void normaleSaetzeSindKeineWuensche() {
        assertNull(Wishes.command("Was wünschst du dir?"));
        assertNull(Wishes.command("Notier dir, dass ich morgen Training habe"));
        assertNull(Wishes.command("Wunsch für die Entwicklung"));
        assertNull(Wishes.command(""));
        assertNull(Wishes.command(null));
    }

    @Test
    public void steuerzeilenAusDerAntwort() {
        String answer = "Notiert, Sir.\n[WUNSCH: Sprachausgabe bricht beim Sprechen ab | Sobald Art im Dauergespräch spricht, "
                + "stoppt die Stimme.]\n[WUNSCH: Jarvis bearbeitet Projekte selbst]";
        List<String[]> w = Wishes.tags(answer);
        assertEquals(2, w.size());
        assertEquals("Sprachausgabe bricht beim Sprechen ab", w.get(0)[0]);
        assertTrue(w.get(0)[1].startsWith("Sobald Art"));
        assertEquals("Jarvis bearbeitet Projekte selbst", w.get(1)[0]);
        assertEquals("", w.get(1)[1]);
        assertEquals("Notiert, Sir.", Wishes.stripTags(answer));
        assertTrue(Wishes.tags("Ganz normale Antwort.").isEmpty());
    }

    @Test
    public void speichernLadenUndKeineDoppelten() {
        String s = Wishes.add("", "Wecker stellen", "per Sprache");
        s = Wishes.add(s, "wecker stellen", "doppelt");
        s = Wishes.add(s, "Tiefere Stimme", "");
        List<Wishes.Wish> list = Wishes.load(s);
        assertEquals(2, list.size());
        assertTrue(list.get(0).open());
        s = Wishes.markSent(s, "Wecker stellen", 12);
        list = Wishes.load(s);
        assertFalse(list.get(0).open());
        assertEquals("#12", list.get(0).status);
        assertTrue(list.get(1).open());
        assertTrue(Wishes.display(s).contains("Aufgabe #12"));
    }

    @Test
    public void tabsUndZeilenumbruecheZerstoerenDasFormatNicht() {
        String s = Wishes.add("", "Titel\tmit Tab", "Zeile eins\nZeile zwei");
        List<Wishes.Wish> list = Wishes.load(s);
        assertEquals(1, list.size());
        assertEquals("Titel mit Tab", list.get(0).title);
        assertEquals("Zeile eins Zeile zwei", list.get(0).body);
    }

    @Test
    public void issueJson() {
        String json = Wishes.issueJson("Stimme \"tiefer\"", "Bitte tiefer.", "5.11.2026");
        assertEquals("Wunsch: Stimme \"tiefer\"", AgentReply.text(json, "title"));
        assertTrue(AgentReply.text(json, "body").startsWith("Bitte tiefer."));
        assertTrue(AgentReply.text(json, "body").contains("5.11.2026"));
    }

    @Test
    public void titelAusFreiemText() {
        assertEquals("Er soll leiser sprechen", Wishes.titleFrom("er soll leiser sprechen. Und zwar im Spiel."));
        String lang = Wishes.titleFrom("a".repeat(10) + " " + "b".repeat(30) + " " + "c".repeat(40));
        assertTrue(lang.length() <= 72);
    }

    /** Die echten Titel vom 8. Nov.: neun Issues, drei Themen. */
    @Test
    public void gleichesThemaWirdErkannt() {
        String s = Wishes.add("", "TFT-Coaching priorisieren", "");
        s = Wishes.markSent(s, "TFT-Coaching priorisieren", 23);
        assertEquals("#23", Wishes.similar(s, "TFT-Coaching als Priorität").status);
        assertNotNull(Wishes.similar(s, "TFT-Coaching beschleunigen"));
        assertNotNull(Wishes.similar(s, "Schnelles, effizientes TFT-Coaching"));

        s = Wishes.add(s, "Dazwischenreden zuverlässig machen", "");
        assertNotNull(Wishes.similar(s, "Dazwischenreden zuverlässiger unterbrechen"));
        assertNotNull(Wishes.similar(s, "Dazwischenreden zuverlässiger"));

        s = Wishes.add(s, "Empfindlichkeit für Jarvis-Abbruch", "");
        assertNotNull(Wishes.similar(s, "Empfindlichkeit für Jarvis-Abbruch")); // gleicher Titel (#26, #30)
    }

    @Test
    public void verschiedeneWuenscheBleibenGetrennt() {
        String s = Wishes.add("", "Dazwischenreden zuverlässig machen", "");
        s = Wishes.add(s, "Stimme bricht mitten im Satz ab", "");
        assertNull(Wishes.similar(s, "Eigene Stimme nicht als Unterbrechung werten"));
        assertNull(Wishes.similar(s, "Kürzere Antworten"));
        assertNull(Wishes.similar(s, "Tiefere Stimme"));
        assertNull(Wishes.similar("", "Kürzere Antworten"));
    }
}
