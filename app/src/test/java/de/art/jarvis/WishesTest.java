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
        assertTrue(Wishes.display(s).contains("(#12, gesendet)"));
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

    /**
     * Alle 33 echten Wünsche bis 8. Nov. (test/resources/wuensche_8nov.tsv) paarweise: Doppelte erkennen, verschiedene
     * nie zusammenlegen. Gruppen derselben Sache laut den Zusammenfassungen der Issues.
     */
    @Test
    public void echteWuensche() throws Exception {
        List<String[]> w = new java.util.ArrayList<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("wuensche_8nov.tsv"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) if (!line.startsWith("#")) w.add(line.split("\t"));
        }
        assertEquals(33, w.size());
        int[][] groups = {{10, 11, 18, 20}, {17, 19, 28, 31, 32, 33, 40}, {24, 27, 29}, {26, 30}, {36, 39}, {39, 47},
                {50, 51, 52}, {41, 42}, {34, 35}};
        int found = 0;
        for (int x = 0; x < w.size(); x++) {
            for (int y = 0; y < x; y++) {
                String stored = Wishes.add("", w.get(y)[1], w.get(y)[2]);
                boolean hit = Wishes.similar(stored, w.get(x)[1], w.get(x)[2]) != null;
                boolean same = false;
                int a = Integer.parseInt(w.get(x)[0]), b = Integer.parseInt(w.get(y)[0]);
                for (int[] g : groups) {
                    boolean ha = false, hb = false;
                    for (int n : g) {
                        ha |= n == a;
                        hb |= n == b;
                    }
                    same |= ha && hb;
                }
                assertFalse("falsch zusammengelegt: #" + a + " mit #" + b, hit && !same);
                if (hit) found++;
            }
        }
        assertTrue("erkannte Doppelte: " + found, found >= 28);   // vorher (nur Titel): 11
        // Der Anlass: die dreifache Stimm-Wunsch (#50–#52).
        String s = Wishes.add("", "Brutalere Stimme", w.get(30)[2]);
        assertNotNull(Wishes.similar(s, w.get(31)[1], w.get(31)[2]));
        assertNotNull(Wishes.similar(s, w.get(32)[1], w.get(32)[2]));
    }

    /** 8. Nov.: „✓“ hieß „gesendet“, Art las „erledigt“. Jetzt der echte Stand von GitHub. */
    @Test
    public void listeZeigtDenEchtenStand() {
        String s = "#17\tDazwischenreden zuverlässig\t\n#19\tDazwischenreden reparieren\t\n"
                + "#21\tKürzere Antworten\t\n#50\tNeu gesendet\t\n" + Wishes.OPEN + "\tNoch lokal\t";
        java.util.Map<Integer, String> st = new java.util.HashMap<>();
        st.put(17, Wishes.ISSUE_OPEN);
        st.put(19, Wishes.ISSUE_MERGED);
        st.put(21, Wishes.ISSUE_DONE);
        String d = Wishes.display(s, st);
        assertTrue(d.contains("○ Dazwischenreden zuverlässig (#17, offen)"));
        assertTrue(d.contains("✓ Kürzere Antworten (#21, erledigt)"));
        assertTrue(d.contains("↗ Neu gesendet (#50, gesendet)"));   // Stand noch unbekannt
        assertTrue(d.contains("✎ Noch lokal (noch nicht gesendet)"));
        assertTrue(d.contains("≈ 1 doppelter Wunsch"));
        assertFalse(d.contains("Dazwischenreden reparieren"));
        assertTrue(d.indexOf("(#17, offen)") < d.indexOf("(#21, erledigt)")); // offene zuerst
        // Ohne geladenen Stand kein falsches Häkchen.
        assertFalse(Wishes.display(s).contains("✓"));
    }

    @Test
    public void standAusGithubAntwort() {
        java.util.Map<Integer, String> st = Wishes.issueStates("["
                + "{\"number\":17,\"state\":\"open\",\"state_reason\":null},"
                + "{\"number\":19,\"state\":\"closed\",\"state_reason\":\"not_planned\"},"
                + "{\"number\":20,\"state\":\"closed\",\"state_reason\":\"duplicate\"},"
                + "{\"number\":21,\"state\":\"closed\",\"state_reason\":\"completed\"},"
                + "{\"number\":22,\"state\":\"open\",\"pull_request\":{}}]");
        assertEquals(Wishes.ISSUE_OPEN, st.get(17));
        assertEquals(Wishes.ISSUE_MERGED, st.get(19));
        assertEquals(Wishes.ISSUE_MERGED, st.get(20));
        assertEquals(Wishes.ISSUE_DONE, st.get(21));
        assertNull(st.get(22)); // Pull Request, kein Wunsch
        assertTrue(Wishes.issueStates("kaputt").isEmpty());
        assertEquals(17, Wishes.issueNumber("#17"));
        assertEquals(0, Wishes.issueNumber(Wishes.OPEN));
    }
}
