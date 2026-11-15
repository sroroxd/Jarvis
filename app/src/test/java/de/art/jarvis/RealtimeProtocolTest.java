package de.art.jarvis;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RealtimeProtocolTest {
    @Test public void sitzungIstAufEinModellUndAudioBegrenzt() throws Exception {
        JSONObject body = RealtimeProtocol.secretRequest();
        assertEquals(60, body.getJSONObject("expires_after").getInt("seconds"));
        JSONObject session = body.getJSONObject("session");
        assertEquals("gpt-realtime-2.1-mini", session.getString("model"));
        assertEquals("audio", session.getJSONArray("output_modalities").getString(0));
        assertEquals(512, session.getInt("max_output_tokens"));
        JSONObject input = session.getJSONObject("audio").getJSONObject("input");
        assertEquals(24000, input.getJSONObject("format").getInt("rate"));
        assertFalse(input.getJSONObject("turn_detection").getBoolean("create_response"));
        assertFalse(input.has("transcription")); // Kein zweites kostenpflichtiges Modell.
        assertTrue(session.getString("instructions").contains("Nicht sicher erkennbar"));
    }

    @Test public void alteZukuenftigeUndVorDerFrageEntstandeneBilderWerdenVerworfen() {
        assertTrue(RealtimeProtocol.fresh(10000, 10000, 10200));
        assertFalse(RealtimeProtocol.fresh(10000, 10000, 11501));
        assertFalse(RealtimeProtocol.fresh(9000, 10000, 10010));
        assertFalse(RealtimeProtocol.fresh(10010, 10000, 10000));
        assertFalse(RealtimeProtocol.fresh(0, 10000, 10010));
    }

    @Test public void bildNurBeiExplizitBestaetigterFrische() throws Exception {
        JSONObject fresh = RealtimeProtocol.question("item_test", "test-jpeg", true);
        assertEquals("user", fresh.getJSONObject("item").getString("role"));
        assertEquals("data:image/jpeg;base64,test-jpeg", fresh.getJSONObject("item").getJSONArray("content").getJSONObject(0).getString("image_url"));
        for (String image : new String[]{null, "", "test-jpeg"}) {
            String absent = RealtimeProtocol.question("item_test", image, false).toString();
            assertFalse(absent.contains("input_image"));
            assertFalse(absent.contains("test-jpeg"));
            assertTrue(absent.contains("Nicht sicher erkennbar"));
        }
    }

    @Test public void quotaUndRateLimitBleibenUnterscheidbar() {
        assertTrue(RealtimeProtocol.error(429, "insufficient_quota").contains("Guthaben"));
        assertTrue(RealtimeProtocol.error(429, "rate_limit_exceeded").contains("Abfragelimit"));
        assertTrue(RealtimeProtocol.error(404, "model_not_found").contains("Kein Ausweichmodell"));
        assertTrue(RealtimeProtocol.error(401, "").contains("abgelehnt"));
    }

    @Test public void technischeFehlercodesSindSichtbarOhneSchluesselOderRohtext() {
        assertTrue(RealtimeProtocol.error(0, "conversation_already_has_active_response").contains("conversation_already_has_active_response"));
        for (String unsafe : new String[]{"ek_private_token", "sk_private_token", "ghp_private_token", "github_pat_private_token", "sk-proj-private", "raw error with spaces"}) {
            assertEquals("", RealtimeProtocol.safeErrorCode(unsafe));
            assertFalse(RealtimeProtocol.error(0, unsafe).contains(unsafe));
        }
    }
}
