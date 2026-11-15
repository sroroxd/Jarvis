package de.art.jarvis;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Offline tests: no OpenAI endpoint and no billable request. */
public class OpenAiClientTest {
    @Test public void convertsTextHistoryAndImage() throws Exception {
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("type","image").put("source", new JSONObject()
                        .put("type","base64").put("media_type","image/jpeg").put("data","YWJj")))
                .put(new JSONObject().put("type","text").put("text","Was siehst du?"));
        JSONArray history = new JSONArray()
                .put(new JSONObject().put("role","assistant").put("content","Guten Morgen."))
                .put(new JSONObject().put("role","user").put("content",parts));
        JSONArray input = OpenAiClient.toInput(history);
        assertEquals(2,input.length());
        assertEquals("output_text",input.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("type"));
        JSONArray imageAndText=input.getJSONObject(1).getJSONArray("content");
        assertEquals("input_image",imageAndText.getJSONObject(0).getString("type"));
        assertEquals("data:image/jpeg;base64,YWJj",imageAndText.getJSONObject(0).getString("image_url"));
        assertEquals("input_text",imageAndText.getJSONObject(1).getString("type"));
    }

    @Test public void extractsOnlyVisibleOutputText() throws Exception {
        JSONArray output = new JSONArray()
                .put(new JSONObject().put("type","reasoning"))
                .put(new JSONObject().put("type","message").put("content",
                        new JSONArray().put(new JSONObject().put("type","output_text").put("text","Hallo"))
                                .put(new JSONObject().put("type","refusal").put("refusal","no"))));
        assertEquals("Hallo", OpenAiClient.outputText(output));
        assertEquals("",OpenAiClient.outputText(null));
    }

    @Test public void acceptsOpenAiModelsAndRemainsStrict() {
        assertTrue(Text.isValidModel("gpt-4.1-mini"));
        assertTrue(Text.isValidModel("claude-sonnet-5-5"));
        assertFalse(Text.isValidModel("https://example.org"));
        assertFalse(Text.isValidModel("gemini-flash"));
    }
}
