package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Gestreamte Antwort wieder zusammensetzen – Format laut Claude-Doku (Server-Sent Events). */
public class StreamAccumulatorTest {

    @Test
    public void openAIWerkzeugkontextBleibtErhaltenUndWirdNichtGesprochen() throws Exception {
        StringBuilder spoken = new StringBuilder();
        StreamAccumulator a = feed(spoken,
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"call_1\",\"name\":\"kalender_lesen\",\"input\":{\"von\":\"2026-11-10\"}}}",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"jarvis_openai_context\",\"id\":\"opaque-context\"}}",
                "data: {\"type\":\"content_block_stop\",\"index\":1}",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}}",
                "data: {\"type\":\"message_stop\"}");
        assertTrue(a.complete());
        assertEquals("2026-11-10", a.content().getJSONObject(0).getJSONObject("input").getString("von"));
        assertEquals("opaque-context", a.content().getJSONObject(1).getString("id"));
        assertEquals("", spoken.toString());
        assertEquals("", ClaudeClient.spokenText(a.content()));
    }

    private static StreamAccumulator feed(StringBuilder spoken, String... lines) throws Exception {
        StreamAccumulator a = new StreamAccumulator(spoken::append);
        for (String l : lines) a.line(l);
        return a;
    }

    @Test
    public void textUndNachdenkenMitSignatur() throws Exception {
        StringBuilder spoken = new StringBuilder();
        StreamAccumulator a = feed(spoken,
                "event: message_start",
                "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"usage\":{\"input_tokens\":12,\"cache_read_input_tokens\":5900,\"output_tokens\":1}}}",
                "",
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"signature_delta\",\"signature\":\"sig==\"}}",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "event: ping",
                "data: {\"type\":\"ping\"}",
                "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"Guten \"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"Abend, Sir.\"}}",
                "data: {\"type\":\"content_block_stop\",\"index\":1}",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":9}}",
                "data: {\"type\":\"message_stop\"}");
        assertEquals("Guten Abend, Sir.", spoken.toString()); // schon unterwegs weitergereicht
        JSONArray c = a.content();
        assertEquals(2, c.length());
        assertEquals("thinking", c.getJSONObject(0).getString("type"));
        assertEquals("sig==", c.getJSONObject(0).getString("signature"));
        assertEquals("Guten Abend, Sir.", c.getJSONObject(1).getString("text"));
        assertEquals("Guten Abend, Sir.", ClaudeClient.spokenText(c));
        assertEquals("end_turn", a.stopReason());
        assertTrue(a.complete());
        assertNull(a.errorType());
        ClaudeClient.Usage u = ClaudeClient.Usage.from(a.usage());
        assertEquals(5900, u.cacheRead);
        assertEquals(9, a.usage().getInt("output_tokens"));
    }

    @Test
    public void werkzeugaufrufKommtStueckweise() throws Exception {
        StringBuilder spoken = new StringBuilder();
        StreamAccumulator a = feed(spoken,
                "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":3}}}",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"kalender_lesen\",\"input\":{}}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"von\\\": \\\"2026-\"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"11-08\\\"}\"}}",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":20}}",
                "data: {\"type\":\"message_stop\"}");
        assertEquals("", spoken.toString());
        JSONObject call = ClaudeClient.clientToolCalls(a.content()).get(0);
        assertEquals("toolu_1", call.getString("id"));
        assertEquals("2026-11-08", call.getJSONObject("input").getString("von"));
        assertEquals("tool_use", a.stopReason());
    }

    @Test
    public void websucheMitQuellenBleibtVollstaendig() throws Exception {
        StringBuilder spoken = new StringBuilder();
        StreamAccumulator a = feed(spoken,
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_1\",\"name\":\"web_search\",\"input\":{}}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"query\\\":\\\"Wetter Hannover\\\"}\"}}",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_1\",\"content\":[{\"type\":\"web_search_result\",\"url\":\"https://example.org\"}]}}",
                "data: {\"type\":\"content_block_stop\",\"index\":1}",
                "data: {\"type\":\"content_block_start\",\"index\":2,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"citations_delta\",\"citation\":{\"type\":\"web_search_result_location\",\"url\":\"https://example.org\"}}}",
                "data: {\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"text_delta\",\"text\":\"Morgen zwölf Grad.\"}}",
                "data: {\"type\":\"content_block_stop\",\"index\":2}",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}",
                "data: {\"type\":\"message_stop\"}");
        JSONArray c = a.content();
        assertEquals(3, c.length());
        assertEquals("Wetter Hannover", c.getJSONObject(0).getJSONObject("input").getString("query"));
        assertEquals(1, c.getJSONObject(1).getJSONArray("content").length());
        assertEquals(1, c.getJSONObject(2).getJSONArray("citations").length());
        assertEquals("Morgen zwölf Grad.", spoken.toString());
        assertTrue(ClaudeClient.clientToolCalls(c).isEmpty());
    }

    @Test
    public void fehlerMittenImStrom() throws Exception {
        StringBuilder spoken = new StringBuilder();
        StreamAccumulator a = feed(spoken,
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Also \"}}",
                "event: error",
                "data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");
        assertEquals("overloaded_error", a.errorType());
        assertEquals("Overloaded", a.errorMessage());
        assertFalse(a.complete());
    }

    @Test
    public void abgerissenerStromIstUnvollstaendig() throws Exception {
        StreamAccumulator a = feed(new StringBuilder(),
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hallo\"}}");
        assertFalse(a.complete());
        assertNull(a.errorType());
    }
}
