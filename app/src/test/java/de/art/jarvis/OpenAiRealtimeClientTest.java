package de.art.jarvis;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.Test;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class OpenAiRealtimeClientTest {
    static class Events implements OpenAiRealtimeClient.Listener {
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        public void ready() { received.add("ready"); }
        public void speechStarted() { received.add("speech"); }
        public void turnCommitted() { received.add("turn"); }
        public void audio(String b) { received.add("audio:" + b); }
        public void transcript(String t) { received.add("text:" + t); }
        public void responseDone(long in, long out) { received.add("done:" + (in + out)); }
        public void error(String e) { received.add("error:" + e); }
        String next() throws Exception { return received.poll(5, TimeUnit.SECONDS); }
    }

    @Test public void kurzlebigesTokenBildAudioUndUnterbrechenUeberEchtenWebsocket() throws Exception {
        OkHttpClient http = new OkHttpClient.Builder().retryOnConnectionFailure(false).build();
        Events listener = new Events();
        BlockingQueue<String> sent = new LinkedBlockingQueue<>();
        BlockingQueue<WebSocket> sockets = new LinkedBlockingQueue<>();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"value\":\"ek_local-test\",\"expires_at\":9999999999}"));
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override public void onOpen(WebSocket ws, Response r) { sockets.add(ws); }
                @Override public void onMessage(WebSocket ws, String text) {
                    sent.add(text);
                    try {
                        if (new JSONObject(text).getString("type").equals("session.update")) ws.send("{\"type\":\"session.updated\"}");
                    } catch (Exception ignored) {}
                }
            }));
            OpenAiRealtimeClient client = new OpenAiRealtimeClient(http, server.url("/client_secrets").toString(),
                    server.url("/realtime").toString().replace("http://", "ws://"), listener);
            try {
                client.start("local-test-only");
                assertEquals("ready", listener.next());
                RecordedRequest tokenRequest = server.takeRequest(5, TimeUnit.SECONDS);
                RecordedRequest socketRequest = server.takeRequest(5, TimeUnit.SECONDS);
                assertEquals("Bearer local-test-only", tokenRequest.getHeader("Authorization"));
                assertEquals("Bearer ek_local-test", socketRequest.getHeader("Authorization"));
                assertFalse(tokenRequest.getBody().readUtf8().contains("local-test-only"));
                assertEquals("session.update", new JSONObject(sent.poll(5, TimeUnit.SECONDS)).getString("type"));
                client.appendAudio("AAAA");
                assertEquals("input_audio_buffer.append", new JSONObject(sent.poll(5, TimeUnit.SECONDS)).getString("type"));
                client.respond("jpeg-test", true);
                String image = sent.poll(5, TimeUnit.SECONDS);
                assertTrue(image.contains("input_image"));
                assertTrue(image.contains("data:image/jpeg;base64,jpeg-test"));
                assertFalse(image.contains("local-test-only"));
                assertEquals("response.create", new JSONObject(sent.poll(5, TimeUnit.SECONDS)).getString("type"));
                WebSocket ws = sockets.poll(5, TimeUnit.SECONDS);
                ws.send("{\"type\":\"response.created\",\"response\":{\"id\":\"r1\"}}");
                ws.send("{\"type\":\"response.output_audio.delta\",\"response_id\":\"r1\",\"item_id\":\"a1\",\"content_index\":0,\"delta\":\"AAAA\"}");
                ws.send("{\"type\":\"response.output_audio_transcript.delta\",\"response_id\":\"r1\",\"delta\":\"Nicht sicher erkennbar.\"}");
                assertEquals("audio:AAAA", listener.next());
                assertEquals("text:Nicht sicher erkennbar.", listener.next());
                client.interrupt(120);
                assertFalse("Keine neue Antwort vor der Abbruchbestätigung", client.canRespond());
                assertEquals("response.cancel", new JSONObject(sent.poll(5, TimeUnit.SECONDS)).getString("type"));
                JSONObject truncate = new JSONObject(sent.poll(5, TimeUnit.SECONDS));
                assertEquals("conversation.item.truncate", truncate.getString("type"));
                assertEquals(120, truncate.getInt("audio_end_ms"));
                ws.send("{\"type\":\"response.output_audio.delta\",\"response_id\":\"r1\",\"delta\":\"obsolete\"}");
                ws.send("{\"type\":\"response.done\",\"response\":{\"id\":\"r1\",\"status\":\"cancelled\"}}");
                ws.send("{\"type\":\"input_audio_buffer.committed\"}");
                assertEquals("turn", listener.next()); // Verspätetes altes Audio wurde verworfen.
                client.respond(null, false);
                assertEquals("conversation.item.delete", new JSONObject(sent.poll(5, TimeUnit.SECONDS)).getString("type"));
                String noImage = sent.poll(5, TimeUnit.SECONDS);
                assertFalse(noImage.contains("input_image"));
                assertTrue(noImage.contains("Nicht sicher erkennbar"));
                sent.poll(5, TimeUnit.SECONDS); // response.create
                ws.send("{\"type\":\"response.created\",\"response\":{\"id\":\"r2\"}}");
                ws.send("{\"type\":\"response.done\",\"response\":{\"id\":\"r2\",\"status\":\"completed\",\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}}");
                assertEquals("done:15", listener.next());
                assertTrue(client.respond(null, false));
                sent.poll(5, TimeUnit.SECONDS); sent.poll(5, TimeUnit.SECONDS); sent.poll(5, TimeUnit.SECONDS);
                client.interrupt(0); // Unterbrechen, bevor response.created angekommen ist.
                assertEquals("response.cancel", new JSONObject(sent.poll(5, TimeUnit.SECONDS)).getString("type"));
                assertFalse(client.canRespond());
                ws.send("{\"type\":\"response.created\",\"response\":{\"id\":\"r3\"}}");
                ws.send("{\"type\":\"input_audio_buffer.committed\"}");
                assertEquals("turn", listener.next());
                assertFalse("Auch nach response.created muss response.done abgewartet werden", client.canRespond());
                assertFalse(client.respond(null, false));
                ws.send("{\"type\":\"response.done\",\"response\":{\"id\":\"r3\",\"status\":\"cancelled\"}}");
                ws.send("{\"type\":\"input_audio_buffer.committed\"}");
                assertEquals("turn", listener.next());
                assertTrue(client.respond(null, false));
                sent.poll(5, TimeUnit.SECONDS); sent.poll(5, TimeUnit.SECONDS); sent.poll(5, TimeUnit.SECONDS);
                ws.send("{\"type\":\"response.done\",\"response\":{\"id\":\"r3\",\"status\":\"cancelled\"}}");
                ws.send("{\"type\":\"input_audio_buffer.committed\"}");
                assertEquals("turn", listener.next());
                assertFalse("Altes response.done gibt keine neue ausstehende Antwort frei", client.canRespond());
                ws.send("{\"type\":\"response.created\",\"response\":{\"id\":\"r4\"}}");
                ws.send("{\"type\":\"response.done\",\"response\":{\"id\":\"r4\",\"status\":\"completed\"}}");
                assertEquals("done:0", listener.next());
                // Mehr als sechs aufeinanderfolgende Antworten nutzen dieselbe Sitzung und dasselbe Token.
                for (int turn = 5; turn <= 11; turn++) {
                    assertTrue("Weitere Antwort " + turn + " ohne neuen Schlüssel", client.respond(null, false));
                    assertEquals("conversation.item.delete", new JSONObject(sent.poll(5, TimeUnit.SECONDS)).getString("type"));
                    assertEquals("conversation.item.create", new JSONObject(sent.poll(5, TimeUnit.SECONDS)).getString("type"));
                    assertEquals("response.create", new JSONObject(sent.poll(5, TimeUnit.SECONDS)).getString("type"));
                    ws.send("{\"type\":\"response.created\",\"response\":{\"id\":\"r" + turn + "\"}}");
                    ws.send("{\"type\":\"response.done\",\"response\":{\"id\":\"r" + turn + "\",\"status\":\"completed\",\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}}");
                    assertEquals("done:15", listener.next());
                }
                assertEquals("Nur ein Token-Aufruf und ein WebSocket-Handshake", 2, server.getRequestCount());
                ws.send("{\"type\":\"error\",\"error\":{\"code\":\"invalid_request_error\",\"message\":\"PRIVATE-DETAILS\"}}");
                String diagnostic = listener.next();
                assertTrue(diagnostic.contains("invalid_request_error"));
                assertFalse(diagnostic.contains("PRIVATE-DETAILS"));
            } finally { client.close(); }
        } finally { http.dispatcher().executorService().shutdownNow(); http.connectionPool().evictAll(); }
    }

    @Test public void quotaFehlerEnthaeltKeineRohantwortUndStartetKeinAusweichmodell() throws Exception {
        OkHttpClient http = new OkHttpClient.Builder().retryOnConnectionFailure(false).build();
        Events listener = new Events();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"PRIVATE-DETAILS\"}}"));
            OpenAiRealtimeClient client = new OpenAiRealtimeClient(http, server.url("/client_secrets").toString(), server.url("/realtime").toString(), listener);
            try {
                client.start("local-test-only");
                String failure = listener.next();
                assertNotNull(failure);
                assertTrue(failure.contains("Guthaben"));
                assertFalse(failure.contains("PRIVATE-DETAILS"));
                assertEquals(1, server.getRequestCount());
            } finally { client.close(); }
        } finally { http.dispatcher().executorService().shutdownNow(); http.connectionPool().evictAll(); }
    }
}
