package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Anfrage- und Antwortformat der Gemini-API (generateContent, Stand 15. Nov. 2026). */
public class GeminiClientTest {

    private static JSONObject msg(String role, String content) throws Exception {
        return new JSONObject().put("role", role).put("content", content);
    }

    @Test
    public void verlaufWirdZuUserUndModel() throws Exception {
        List<JSONObject> h = new ArrayList<>();
        h.add(msg("user", "Wie viel ist 17 mal 3?"));
        h.add(msg("assistant", "51."));
        JSONObject body = GeminiClient.body("Du bist Jarvis.", h, "Und das Doppelte?", null);
        JSONArray c = body.getJSONArray("contents");
        assertEquals(3, c.length());
        assertEquals("user", c.getJSONObject(0).getString("role"));
        assertEquals("model", c.getJSONObject(1).getString("role"));
        assertEquals("51.", c.getJSONObject(1).getJSONArray("parts").getJSONObject(0).getString("text"));
        assertEquals("Und das Doppelte?", c.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"));
        assertEquals("Du bist Jarvis.", body.getJSONObject("system_instruction").getJSONArray("parts")
                .getJSONObject(0).getString("text"));
        assertEquals(GeminiClient.MAX_OUTPUT_TOKENS, body.getJSONObject("generationConfig").getInt("maxOutputTokens"));
    }

    /** Abgebrochene Antwort: zwei Nutzer-Beiträge hintereinander wären ein Rollenfehler. */
    @Test
    public void rollenWechselnImmerAb() throws Exception {
        List<JSONObject> h = new ArrayList<>();
        h.add(msg("user", "Erste Frage"));
        h.add(msg("user", "Zweite Frage"));
        JSONArray c = GeminiClient.body(null, h, "Dritte", null).getJSONArray("contents");
        assertEquals(1, c.length());
        JSONArray parts = c.getJSONObject(0).getJSONArray("parts");
        assertTrue(parts.getJSONObject(0).getString("text").contains("Zweite Frage"));
        assertEquals("Dritte", parts.getJSONObject(parts.length() - 1).getString("text"));
    }

    @Test
    public void bildstehtVorDerFrage() throws Exception {
        JSONArray c = GeminiClient.body("s", new ArrayList<>(), "Was siehst du?", "QUJD").getJSONArray("contents");
        JSONArray parts = c.getJSONObject(0).getJSONArray("parts");
        assertEquals("image/jpeg", parts.getJSONObject(0).getJSONObject("inline_data").getString("mime_type"));
        assertEquals("QUJD", parts.getJSONObject(0).getJSONObject("inline_data").getString("data"));
        assertEquals("Was siehst du?", parts.getJSONObject(1).getString("text"));
    }

    @Test
    public void datenstromWirdGelesen() {
        assertEquals("Hallo ", GeminiClient.parseSseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hallo \"}],\"role\":\"model\"}}]}"));
        // Gedanken-Teile werden nie vorgelesen.
        assertEquals("Sir.", GeminiClient.parseSseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"…\",\"thought\":true},{\"text\":\"Sir.\"}]}}]}"));
        assertEquals("", GeminiClient.parseSseLine("data: {\"usageMetadata\":{\"totalTokenCount\":5}}"));
        assertEquals("", GeminiClient.parseSseLine("data: kaputt"));
        assertNull(GeminiClient.parseSseLine(""));
        assertNull(GeminiClient.parseSseLine(": keep-alive"));
    }

    @Test
    public void fehlerSindVorlesbar() {
        assertTrue(GeminiClient.describeError(400, "{\"error\":{\"message\":\"API key not valid.\"}}").contains("ungültig"));
        assertTrue(GeminiClient.describeError(429, "").contains("kostenlose"));
        assertTrue(GeminiClient.describeError(404, "").contains("Modell"));
        assertTrue(GeminiClient.describeError(503, "").contains("gestört"));
    }
}
