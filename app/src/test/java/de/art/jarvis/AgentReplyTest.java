package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Zerlegung der Antworten des Jarvis-Agenten – ohne Android, läuft in den automatischen Tests mit. */
public class AgentReplyTest {

    @Test
    public void erfolgLiestAntwortUndKosten() {
        String json = "{\"antwort\":\"Die Datei liegt im Arbeitsordner, Sir.\",\"sitzung\":\"abc-123\","
                + "\"kosten\":0.0412,\"dauer\":18345,\"schritte\":3}";
        AgentReply r = AgentReply.parse(200, json);
        assertTrue(r.ok());
        assertEquals("Die Datei liegt im Arbeitsordner, Sir.", r.antwort);
        assertEquals(0.0412, r.kosten, 1e-9);
        assertEquals(18345L, r.dauer);
    }

    @Test
    public void fehlerFeldHatVorrangVorStatus() {
        AgentReply r = AgentReply.parse(200, "{\"fehler\":\"Der Auftrag hat zu lange gedauert.\"}");
        assertFalse(r.ok());
        assertEquals("Der Auftrag hat zu lange gedauert.", r.fehler);
    }

    @Test
    public void falschesTokenWirdErklaert() {
        AgentReply r = AgentReply.parse(401, "");
        assertFalse(r.ok());
        assertTrue(r.fehler.contains("Token"));
    }

    @Test
    public void agentBeschaeftigt() {
        AgentReply r = AgentReply.parse(409, "{}");
        assertFalse(r.ok());
        assertTrue(r.fehler.contains("letzten Auftrag"));
    }

    @Test
    public void leereAntwortGiltAlsFehler() {
        assertFalse(AgentReply.parse(200, "{\"antwort\":\"\"}").ok());
        assertFalse(AgentReply.parse(200, "").ok());
        assertFalse(AgentReply.parse(200, "kein json").ok());
    }

    @Test
    public void maskierteZeichenWerdenEntpackt() {
        String json = "{\"antwort\":\"Zeile eins\\nZeile \\\"zwei\\\" mit \\\\ und \\u00e4\"}";
        AgentReply r = AgentReply.parse(200, json);
        assertTrue(r.ok());
        assertEquals("Zeile eins\nZeile \"zwei\" mit \\ und ä", r.antwort);
    }

    @Test
    public void gleichnamigesFeldImUnterobjektWirdIgnoriert() {
        // „antwort“ steckt auch in einem Unterobjekt – nur die oberste Ebene zählt.
        String json = "{\"zusatz\":{\"antwort\":\"falsch\"},\"antwort\":\"richtig\"}";
        assertEquals("richtig", AgentReply.parse(200, json).antwort);
    }

    @Test
    public void feldnameInnerhalbEinesTextesTaeuschtNicht() {
        String json = "{\"antwort\":\"ich habe \\\"fehler\\\": nichts gefunden\"}";
        AgentReply r = AgentReply.parse(200, json);
        assertTrue(r.ok());
        assertTrue(r.antwort.contains("nichts gefunden"));
    }

    @Test
    public void fehlendeZahlenfelderSindNull() {
        AgentReply r = AgentReply.parse(200, "{\"antwort\":\"ok\"}");
        assertTrue(r.ok());
        assertEquals(0.0, r.kosten, 1e-9);
        assertEquals(0L, r.dauer);
    }

    @Test
    public void jsonTextMaskiertKorrekt() {
        assertEquals("\"a\\\"b\"", AgentReply.jsonText("a\"b"));
        assertEquals("\"a\\\\b\"", AgentReply.jsonText("a\\b"));
        assertEquals("\"a\\nb\"", AgentReply.jsonText("a\nb"));
        assertEquals("\"\"", AgentReply.jsonText(null));
    }
}
