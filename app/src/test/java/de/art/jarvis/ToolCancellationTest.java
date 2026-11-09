package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Verspätete KI-Antworten dürfen nach „Gespräch beenden“ keinen Termin mehr anlegen. */
public class ToolCancellationTest {
    private static JSONArray calls() throws JSONException {
        return new JSONArray()
                .put(new JSONObject().put("type", "tool_use").put("id", "termin-1").put("name", "termin_anlegen"))
                .put(new JSONObject().put("type", "tool_use").put("id", "termin-2").put("name", "termin_anlegen"));
    }

    private static ClaudeClient.ToolRunner recording(AtomicInteger writes, Runnable afterWrite) {
        return new ClaudeClient.ToolRunner() {
            @Override public JSONArray definitions() { return new JSONArray(); }
            @Override public JSONObject run(JSONObject call) throws JSONException {
                writes.incrementAndGet();
                afterWrite.run();
                return new JSONObject().put("type", "tool_result").put("tool_use_id", call.getString("id"));
            }
        };
    }

    @Test public void beendeteFrageFuehrtKeinenKalenderAuftragAus() throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger writes = new AtomicInteger();
        ClaudeClient.ToolRunner tools = ClaudeClient.whileActive(recording(writes, () -> {}), active::get);
        JSONArray lateReply = calls();
        active.set(false); // Art beendet das Gespräch, während die Antwort noch unterwegs ist.
        assertThrows(CancellationException.class, () -> ClaudeClient.runClientTools(tools, lateReply));
        assertEquals(0, writes.get());
        assertFalse(tools.isActive());
    }

    @Test public void abbruchZwischenZweiAufrufenVerhindertDenZweiten() throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger writes = new AtomicInteger();
        ClaudeClient.ToolRunner tools = ClaudeClient.whileActive(
                recording(writes, () -> active.set(false)), active::get);
        assertThrows(CancellationException.class, () -> ClaudeClient.runClientTools(tools, calls()));
        assertEquals(1, writes.get()); // Bereits begonnene Änderungen werden nicht rückgängig gemacht.
    }

    @Test public void laufendeFrageErhaeltAlleZugeordnetenErgebnisse() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        ClaudeClient.ToolRunner tools = ClaudeClient.whileActive(recording(writes, () -> {}), () -> true);
        JSONArray results = ClaudeClient.runClientTools(tools, calls());
        assertTrue(tools.isActive());
        assertEquals(2, writes.get());
        assertEquals("termin-1", results.getJSONObject(0).getString("tool_use_id"));
        assertEquals("termin-2", results.getJSONObject(1).getString("tool_use_id"));
    }

    @Test public void direktAufgerufenerRunnerBeachtetDenAbbruchEbenfalls() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        ClaudeClient.ToolRunner tools = ClaudeClient.whileActive(recording(writes, () -> {}), () -> false);
        assertThrows(CancellationException.class, () -> tools.run(calls().getJSONObject(0)));
        assertEquals(0, writes.get());
    }
}
