package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import java.util.List;

/** Prüft, was aus einer Claude-Antwort vorgelesen wird und welche Werkzeuge das Handy ausführen muss. */
public class ClaudeClientTest {

    @Test
    public void normaleAntwortBleibtWieBisher() throws Exception {
        JSONArray c = new JSONArray("[{\"type\":\"thinking\",\"thinking\":\"…\"},"
                + "{\"type\":\"text\",\"text\":\"Guten Abend, Sir.\"}]");
        assertEquals("Guten Abend, Sir.", ClaudeClient.spokenText(c));
    }

    @Test
    public void ansageVorDerWebsucheWirdNichtVorgelesen() throws Exception {
        JSONArray c = new JSONArray("["
                + "{\"type\":\"text\",\"text\":\"Ich schaue kurz nach.\"},"
                + "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_1\",\"name\":\"web_search\",\"input\":{\"query\":\"Wetter Hannover\"}},"
                + "{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_1\",\"content\":[]},"
                + "{\"type\":\"text\",\"text\":\"Morgen wird es \",\"citations\":[{\"type\":\"web_search_result_location\"}]},"
                + "{\"type\":\"text\",\"text\":\"zwölf Grad und trocken.\"}]");
        assertEquals("Morgen wird es zwölf Grad und trocken.", ClaudeClient.spokenText(c));
        assertTrue(ClaudeClient.clientToolCalls(c).isEmpty()); // Websuche läuft auf dem Server
    }

    @Test
    public void kalenderAufrufWirdErkannt() throws Exception {
        JSONArray c = new JSONArray("["
                + "{\"type\":\"text\",\"text\":\"Moment.\"},"
                + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"kalender_lesen\",\"input\":{\"von\":\"2026-11-02\"}}]");
        List<org.json.JSONObject> calls = ClaudeClient.clientToolCalls(c);
        assertEquals(1, calls.size());
        assertEquals("kalender_lesen", calls.get(0).getString("name"));
        assertEquals("toolu_1", calls.get(0).getString("id"));
        // Kein Text nach dem Werkzeug → Rückfall auf den vorhandenen Text statt Stille.
        assertEquals("Moment.", ClaudeClient.spokenText(c));
    }

    @Test
    public void leereOderFehlendeAntwort() throws Exception {
        assertEquals("", ClaudeClient.spokenText(null));
        assertEquals("", ClaudeClient.spokenText(new JSONArray()));
    }

    @Test
    public void eineWebsucheFuerAlltagUndTftMeta() throws Exception {
        org.json.JSONObject t = ClaudeClient.webSearchTool(3);
        assertEquals("web_search_20250305", t.getString("type"));
        assertEquals("web_search", t.getString("name"));
        assertEquals(3, t.getInt("max_uses"));
        assertEquals("DE", t.getJSONObject("user_location").getString("country"));
    }

    @Test
    public void metaRechercheOhneZwischenansagen() throws Exception {
        // Die Tages-Meta nutzt dieselbe Auswertung: nur der Text nach der letzten Suche wird gespeichert.
        JSONArray c = new JSONArray("["
                + "{\"type\":\"text\",\"text\":\"Ich recherchiere die aktuelle Meta.\"},"
                + "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_1\",\"name\":\"web_search\",\"input\":{\"query\":\"TFT meta\"}},"
                + "{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_1\",\"content\":[]},"
                + "{\"type\":\"text\",\"text\":\"Lass mich noch die Patchnotes prüfen.\"},"
                + "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_2\",\"name\":\"web_search\",\"input\":{\"query\":\"TFT patch\"}},"
                + "{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_2\",\"content\":[]},"
                + "{\"type\":\"text\",\"text\":\"Set 17, Patch 16.21.\"}]");
        assertEquals("Set 17, Patch 16.21.", ClaudeClient.spokenText(c));
    }

    @Test
    public void websuchFehlerWirdErkannt() {
        assertTrue(ClaudeClient.mentionsWebSearch("Web search is not enabled for this organization"));
        assertTrue(ClaudeClient.mentionsWebSearch("tools.0: web_search_20250305 is not supported"));
        assertFalse(ClaudeClient.mentionsWebSearch("max_tokens: too large"));
        assertFalse(ClaudeClient.mentionsWebSearch(null));
    }

    @Test
    public void grundanweisungGehtMitCacheMarkeRaus() throws Exception {
        org.json.JSONObject body = ClaudeClient.body("claude-sonnet-5-5", 1024, "disabled", null,
                "Du bist JARVIS.", new JSONArray(), null);
        org.json.JSONArray system = body.getJSONArray("system");
        assertEquals(1, system.length());
        org.json.JSONObject block = system.getJSONObject(0);
        assertEquals("text", block.getString("type"));
        assertEquals("Du bist JARVIS.", block.getString("text"));
        assertEquals("ephemeral", block.getJSONObject("cache_control").getString("type"));
        assertFalse(body.has("cache_control")); // keine automatische Marke am Verlaufsende
        assertFalse(body.has("tools"));
        assertFalse(body.has("temperature"));
    }

    @Test
    public void leereGrundanweisungFaelltWeg() throws Exception {
        org.json.JSONObject body = ClaudeClient.body("claude-sonnet-5-5", 1024, null, null, "  ", new JSONArray(), null);
        assertFalse(body.has("system")); // leere Textblöcke lehnt die API ab
    }

    @Test
    public void cacheVerbrauchWirdGelesen() throws Exception {
        ClaudeClient.Usage gelesen = ClaudeClient.Usage.from(new org.json.JSONObject(
                "{\"input_tokens\":1300,\"cache_read_input_tokens\":2450,\"cache_creation_input_tokens\":0,\"output_tokens\":40}"));
        assertEquals(2450, gelesen.cacheRead);
        assertEquals("Cache 2,5 Tsd. gelesen", gelesen.summary());

        ClaudeClient.Usage neu = ClaudeClient.Usage.from(new org.json.JSONObject(
                "{\"input_tokens\":1300,\"cache_creation_input_tokens\":2450}"));
        assertEquals("Cache 2,5 Tsd. neu", neu.summary());

        ClaudeClient.Usage ohne = ClaudeClient.Usage.from(new org.json.JSONObject("{\"input_tokens\":300}"));
        assertEquals("ohne Cache", ohne.summary());
        assertEquals(null, ClaudeClient.Usage.from(null));
    }

    @Test
    public void netzZeitenWerdenZerlegt() {
        ClaudeClient.Net net = new ClaudeClient.Net(1_000, 389_120);
        assertEquals(null, net.summary()); // noch nichts hochgeladen
        net.uploaded = 1_400;
        assertEquals(null, net.summary()); // noch kein Text
        net.firstText = 2_300;
        assertEquals("Senden 0,4 s (380 KB) · Claude 0,9 s", net.summary());
    }
}
