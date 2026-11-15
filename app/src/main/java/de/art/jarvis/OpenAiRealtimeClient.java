package de.art.jarvis;

import org.json.JSONObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** Nativer WebSocket-Client. Der dauerhafte Schlüssel geht ausschließlich an client_secrets. */
final class OpenAiRealtimeClient implements AutoCloseable {
    interface Listener {
        void ready();
        void speechStarted();
        void turnCommitted();
        void audio(String base64);
        void transcript(String text);
        void responseDone(long inputTokens, long outputTokens);
        void error(String safeMessage);
    }
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient http;
    private final String tokenUrl;
    private final String socketUrl;
    private final Listener listener;
    private final Set<String> cancelled = new HashSet<>();
    private Call tokenCall;
    private WebSocket socket;
    private boolean closed;
    private boolean ready;
    private String responseId = "";
    private String assistantItem = "";
    private int contentIndex;
    private String imageItem = "";
    private boolean responsePending;
    private boolean cancelPending;

    OpenAiRealtimeClient(Listener listener) {
        this(new OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build(),
                "https://api.openai.com/v1/realtime/client_secrets",
                "wss://api.openai.com/v1/realtime?model=" + RealtimeProtocol.MODEL, listener);
    }

    /** Nur für lokale Protokolltests: keine konfigurierbaren Endpunkte in der App-Oberfläche. */
    OpenAiRealtimeClient(OkHttpClient http, String tokenUrl, String socketUrl, Listener listener) {
        this.http = http; this.tokenUrl = tokenUrl; this.socketUrl = socketUrl; this.listener = listener;
    }

    synchronized void start(String key) {
        if (closed || tokenCall != null || socket != null) return;
        try {
            Request request = new Request.Builder().url(tokenUrl).header("Authorization", "Bearer " + key)
                    .post(RequestBody.create(RealtimeProtocol.secretRequest().toString(), JSON)).build();
            tokenCall = http.newCall(request);
            tokenCall.enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { fail(RealtimeProtocol.error(0, "")); }
                @Override public void onResponse(Call call, Response response) {
                    try (Response r = response) {
                        String body = r.body() == null ? "{}" : r.body().string();
                        JSONObject data = new JSONObject(body);
                        if (!r.isSuccessful()) { fail(RealtimeProtocol.error(r.code(), errorCode(data))); return; }
                        String secret = data.optString("value", "");
                        if (!secret.startsWith("ek_")) { fail("OpenAI hat kein gültiges kurzlebiges Sitzungstoken geliefert."); return; }
                        connect(secret); // Das Token wird nur im Speicher und nur für den Handshake verwendet.
                    } catch (Exception e) { fail("OpenAI-Sitzungsantwort konnte nicht gelesen werden."); }
                }
            });
        } catch (Exception e) { fail("OpenAI-Sitzung konnte nicht vorbereitet werden."); }
    }

    private synchronized void connect(String ephemeral) {
        if (closed) return;
        socket = http.newWebSocket(new Request.Builder().url(socketUrl)
                .header("Authorization", "Bearer " + ephemeral).build(), new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                synchronized (OpenAiRealtimeClient.this) {
                    if (closed) { webSocket.cancel(); return; }
                    socket = webSocket;
                    try { send(new JSONObject().put("type", "session.update").put("session", RealtimeProtocol.session())); }
                    catch (Exception e) { fail("OpenAI-Sitzung konnte nicht konfiguriert werden."); }
                }
            }
            @Override public void onMessage(WebSocket webSocket, String text) { receive(text); }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                fail(RealtimeProtocol.error(response == null ? 0 : response.code(), ""));
            }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                fail("OpenAI-Verbindung wurde beendet. Erneuter Start nur per Antippen.");
            }
        });
    }

    synchronized void appendAudio(String base64) {
        if (!ready || closed) return;
        try { send(new JSONObject().put("type", "input_audio_buffer.append").put("audio", base64)); }
        catch (Exception e) { fail("Mikrofonton konnte nicht gesendet werden."); }
    }

    synchronized boolean canRespond() { return ready && !closed && !responsePending && responseId.isEmpty(); }

    synchronized boolean respond(String jpeg, boolean fresh) {
        if (!canRespond()) return false;
        try {
            if (!imageItem.isEmpty()) send(new JSONObject().put("type", "conversation.item.delete").put("item_id", imageItem));
            imageItem = "item_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            send(RealtimeProtocol.question(imageItem, jpeg, fresh));
            assistantItem = "";
            responsePending = true;
            cancelPending = false;
            send(new JSONObject().put("type", "response.create"));
            return !closed;
        } catch (Exception e) { fail("Die Bildanfrage konnte nicht gesendet werden."); }
        return false;
    }

    synchronized void forgetImage() {
        try {
            if (!imageItem.isEmpty()) send(new JSONObject().put("type", "conversation.item.delete").put("item_id", imageItem));
            imageItem = "";
        } catch (Exception e) { fail("Das alte Bildschirmbild konnte nicht verworfen werden."); }
    }

    synchronized void interrupt(int playedMs) {
        if (closed) return;
        try {
            if (responsePending || !responseId.isEmpty()) {
                cancelPending = responsePending;
                if (!responseId.isEmpty()) cancelled.add(responseId);
                send(new JSONObject().put("type", "response.cancel"));
            }
            if (!assistantItem.isEmpty()) send(new JSONObject().put("type", "conversation.item.truncate")
                    .put("item_id", assistantItem).put("content_index", contentIndex).put("audio_end_ms", Math.max(0, playedMs)));
            responseId = ""; assistantItem = "";
        } catch (Exception e) { fail("Die Antwort konnte nicht sauber unterbrochen werden."); }
    }

    private synchronized void receive(String raw) {
        if (closed) return;
        try {
            JSONObject event = new JSONObject(raw);
            String type = event.optString("type");
            switch (type) {
                case "session.updated":
                    if (!ready) { ready = true; listener.ready(); }
                    break;
                case "input_audio_buffer.speech_started": listener.speechStarted(); break;
                case "input_audio_buffer.committed": listener.turnCommitted(); break;
                case "response.created":
                    responsePending = false;
                    String id = event.getJSONObject("response").getString("id");
                    if (cancelPending) { cancelled.add(id); cancelPending = false; break; }
                    responseId = id;
                    break;
                case "response.output_audio.delta":
                    if (!active(event)) break;
                    assistantItem = event.optString("item_id"); contentIndex = event.optInt("content_index", 0);
                    listener.audio(event.getString("delta"));
                    break;
                case "response.output_audio_transcript.delta":
                    if (active(event)) listener.transcript(event.optString("delta"));
                    break;
                case "response.done":
                    JSONObject response = event.getJSONObject("response");
                    String doneId = response.optString("id");
                    boolean obsolete = cancelled.remove(doneId);
                    if (obsolete) break;
                    if (!doneId.equals(responseId)) break;
                    responseId = "";
                    if ("failed".equals(response.optString("status"))) {
                        JSONObject details = response.optJSONObject("status_details");
                        fail(RealtimeProtocol.error(0, details == null ? "" : errorCode(details))); return;
                    }
                    JSONObject usage = response.optJSONObject("usage");
                    listener.responseDone(usage == null ? 0 : usage.optLong("input_tokens"), usage == null ? 0 : usage.optLong("output_tokens"));
                    break;
                case "error":
                    String code = errorCode(event);
                    if ("response_cancel_not_active".equals(code)) break;
                    fail(RealtimeProtocol.error(0, code));
                    break;
                default: break;
            }
        } catch (Exception e) { fail("OpenAI hat eine unlesbare Realtime-Nachricht gesendet."); }
    }

    private boolean active(JSONObject event) {
        String id = event.optString("response_id");
        return !responseId.isEmpty() && responseId.equals(id) && !cancelled.contains(id);
    }

    private static String errorCode(JSONObject body) {
        JSONObject err = body.optJSONObject("error");
        return err == null ? "" : err.optString("code", err.optString("type", ""));
    }

    private void send(JSONObject event) {
        if (socket == null || !socket.send(event.toString())) fail("OpenAI-Verbindung ist nicht mehr sendebereit.");
    }

    private synchronized void fail(String message) {
        if (closed) return;
        close();
        listener.error(message);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true; ready = false;
        if (tokenCall != null) tokenCall.cancel();
        if (socket != null) socket.cancel();
        cancelled.clear();
    }
}
