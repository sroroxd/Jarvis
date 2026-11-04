package de.art.jarvis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Base64;

public class LiveProtocolTest {

    @Test
    public void setupContainsModelVoiceCompressionAndNoHandleForNewSession() throws Exception {
        JSONObject s = new JSONObject(LiveProtocol.setupMessage("gemini-3.8-live", "Charon", "Hallo", null)).getJSONObject("setup");
        assertEquals("models/gemini-3.8-live", s.getString("model"));
        assertEquals("AUDIO", s.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0));
        assertEquals("Charon", s.getJSONObject("generationConfig").getJSONObject("speechConfig")
                .getJSONObject("voiceConfig").getJSONObject("prebuiltVoiceConfig").getString("voiceName"));
        assertTrue(s.has("contextWindowCompression"));
        assertTrue(s.has("inputAudioTranscription"));
        assertTrue(s.has("outputAudioTranscription"));
        assertFalse(s.getJSONObject("sessionResumption").has("handle"));
    }

    @Test
    public void setupWithHandleResumesSession() throws Exception {
        JSONObject s = new JSONObject(LiveProtocol.setupMessage("gemini-3.8-live", "Fenrir", "x", "abc123")).getJSONObject("setup");
        assertEquals("abc123", s.getJSONObject("sessionResumption").getString("handle"));
        assertEquals("Fenrir", s.getJSONObject("generationConfig").getJSONObject("speechConfig")
                .getJSONObject("voiceConfig").getJSONObject("prebuiltVoiceConfig").getString("voiceName"));
    }

    @Test
    public void emptyVoiceFallsBackToDefault() throws Exception {
        JSONObject s = new JSONObject(LiveProtocol.setupMessage("m", "", "x", null)).getJSONObject("setup");
        assertEquals(LiveProtocol.DEFAULT_VOICE, s.getJSONObject("generationConfig").getJSONObject("speechConfig")
                .getJSONObject("voiceConfig").getJSONObject("prebuiltVoiceConfig").getString("voiceName"));
    }

    @Test
    public void audioChunkUsesPcm16kMimeAndBase64() throws Exception {
        byte[] pcm = {1, 2, 3, 4};
        JSONObject a = new JSONObject(LiveProtocol.audioChunk(pcm)).getJSONObject("realtimeInput").getJSONObject("audio");
        assertEquals("audio/pcm;rate=16000", a.getString("mimeType"));
        assertArrayEquals(pcm, Base64.getDecoder().decode(a.getString("data")));
    }

    @Test
    public void videoFrameIsJpeg() throws Exception {
        JSONObject v = new JSONObject(LiveProtocol.videoFrame(new byte[]{9})).getJSONObject("realtimeInput").getJSONObject("video");
        assertEquals("image/jpeg", v.getString("mimeType"));
    }

    @Test
    public void textAndStreamEndMessages() throws Exception {
        assertEquals("Hi", new JSONObject(LiveProtocol.text("Hi")).getJSONObject("realtimeInput").getString("text"));
        assertTrue(new JSONObject(LiveProtocol.audioStreamEnd()).getJSONObject("realtimeInput").getBoolean("audioStreamEnd"));
    }

    @Test
    public void parsesSetupComplete() throws Exception {
        LiveProtocol.Event e = LiveProtocol.parse("{\"setupComplete\":{}}");
        assertTrue(e.setupComplete);
        assertEquals(0, e.audio.length);
    }

    @Test
    public void parsesAudioTurnCompleteAndTranscripts() throws Exception {
        byte[] pcm = {10, 20, 30, 40};
        String b64 = Base64.getEncoder().encodeToString(pcm);
        String json = "{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\""
                + b64 + "\"}}]},\"turnComplete\":true},"
                + "\"inputTranscription\":{\"text\":\"Wie spät\"},\"outputTranscription\":{\"text\":\"Es ist zehn\"}}";
        LiveProtocol.Event e = LiveProtocol.parse(json);
        assertArrayEquals(pcm, e.audio);
        assertTrue(e.turnComplete);
        assertFalse(e.interrupted);
        assertEquals("Wie spät", e.inputText);
        assertEquals("Es ist zehn", e.outputText);
    }

    @Test
    public void ignoresNonAudioParts() throws Exception {
        String json = "{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"text\":\"hallo\"}]}}}";
        LiveProtocol.Event e = LiveProtocol.parse(json);
        assertEquals(0, e.audio.length);
        assertFalse(e.turnComplete);
    }

    @Test
    public void parsesInterruption() throws Exception {
        LiveProtocol.Event e = LiveProtocol.parse("{\"serverContent\":{\"interrupted\":true}}");
        assertTrue(e.interrupted);
    }

    @Test
    public void parsesResumptionHandleOnlyWhenResumable() throws Exception {
        LiveProtocol.Event ok = LiveProtocol.parse(
                "{\"sessionResumptionUpdate\":{\"newHandle\":\"h1\",\"resumable\":true}}");
        assertEquals("h1", ok.resumeHandle);
        LiveProtocol.Event notYet = LiveProtocol.parse(
                "{\"sessionResumptionUpdate\":{\"newHandle\":\"h2\",\"resumable\":false}}");
        assertNull(notYet.resumeHandle);
    }

    @Test
    public void parsesGoAwayTimeLeft() throws Exception {
        LiveProtocol.Event e = LiveProtocol.parse("{\"goAway\":{\"timeLeft\":\"58.5s\"}}");
        assertEquals(58500, e.goAwayMs);
        assertEquals(-1, LiveProtocol.parse("{}").goAwayMs);
    }

    @Test
    public void durationParsingIsTolerant() {
        assertEquals(0, LiveProtocol.parseDurationMs(""));
        assertEquals(0, LiveProtocol.parseDurationMs("kaputt"));
        assertEquals(1500, LiveProtocol.parseDurationMs("1.5s"));
    }

    @Test
    public void detectsToolCallWithoutHandlingIt() throws Exception {
        assertTrue(LiveProtocol.parse("{\"toolCall\":{\"functionCalls\":[]}}").toolCall);
    }
}
