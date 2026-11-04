package de.art.jarvis;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.junit.Test;

/** Prüft, was von der täglichen Meta-Recherche als Meta-Text übrig bleibt. */
public class ClaudeClientMetaTest {

    @Test
    public void nurDerTextNachDerLetztenSucheZaehlt() throws Exception {
        JSONArray c = new JSONArray("["
                + "{\"type\":\"text\",\"text\":\"Ich recherchiere die aktuelle Meta.\"},"
                + "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_1\",\"name\":\"web_search\",\"input\":{\"query\":\"TFT meta\"}},"
                + "{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_1\",\"content\":[]},"
                + "{\"type\":\"text\",\"text\":\"Lass mich noch die Patchnotes prüfen.\"},"
                + "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_2\",\"name\":\"web_search\",\"input\":{\"query\":\"TFT patch\"}},"
                + "{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_2\",\"content\":[]},"
                + "{\"type\":\"text\",\"text\":\"Set 17, Patch 16.21. \",\"citations\":[{\"type\":\"web_search_result_location\"}]},"
                + "{\"type\":\"text\",\"text\":\"Stärkste Comp: …\"}]");
        assertEquals("Set 17, Patch 16.21. Stärkste Comp: …", ClaudeClient.spokenText(c));
    }

    @Test
    public void ohneSucheBleibtAllesErhalten() throws Exception {
        JSONArray c = new JSONArray("[{\"type\":\"thinking\",\"thinking\":\"…\"},"
                + "{\"type\":\"text\",\"text\":\"Guten Abend, Sir.\"}]");
        assertEquals("Guten Abend, Sir.", ClaudeClient.spokenText(c));
    }

    @Test
    public void keinTextNachDerSucheFaelltAufAllenTextZurueck() throws Exception {
        JSONArray c = new JSONArray("["
                + "{\"type\":\"text\",\"text\":\"Teilergebnis.\"},"
                + "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_1\",\"name\":\"web_search\",\"input\":{}}]");
        assertEquals("Teilergebnis.", ClaudeClient.spokenText(c));
        assertEquals("", ClaudeClient.spokenText(null));
        assertEquals("", ClaudeClient.spokenText(new JSONArray()));
    }
}
