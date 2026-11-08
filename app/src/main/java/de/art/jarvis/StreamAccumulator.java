package de.art.jarvis;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Setzt eine gestreamte Claude-Antwort (Server-Sent Events) wieder zu der Antwort zusammen, die ohne
 * Streaming auf einmal käme – mit allen Inhaltsblöcken, damit die Werkzeug-Schleife sie unverändert
 * zurückschicken kann (Nachdenkblöcke samt Signatur, Werkzeugaufrufe, Suchergebnisse, Quellenangaben).
 *
 * <p>Textstücke reicht er schon beim Eintreffen weiter: So kann Jarvis den ersten Satz sprechen,
 * während Claude noch schreibt.
 *
 * <p>Ereignisse laut Doku: message_start, content_block_start, content_block_delta (text_delta,
 * thinking_delta, signature_delta, input_json_delta, citations_delta), content_block_stop,
 * message_delta (stop_reason, usage), message_stop, ping und error.
 *
 * <p>Reine Java-Logik ohne Android, daher automatisch testbar.
 */
final class StreamAccumulator {
    /** Empfängt jedes neue Textstück eines Textblocks. */
    interface TextSink {
        void text(String delta);
    }

    private final TextSink sink;
    private final JSONArray content = new JSONArray();
    /** Werkzeug-Eingaben kommen als JSON-Bruchstücke und werden erst am Blockende zusammengesetzt. */
    private final Map<Integer, StringBuilder> partialJson = new HashMap<>();
    private JSONObject usage;
    private String stopReason = "";
    private String errorType;
    private String errorMessage = "";
    private boolean stopped;

    StreamAccumulator(TextSink sink) {
        this.sink = sink;
    }

    /** Eine Zeile des Datenstroms. Nur „data:“-Zeilen tragen Inhalt; der Ereignistyp steht auch dort. */
    void line(String line) throws JSONException {
        if (line == null || !line.startsWith("data:")) return;
        String data = line.substring(5).trim();
        if (data.isEmpty()) return;
        event(new JSONObject(data));
    }

    void event(JSONObject ev) throws JSONException {
        switch (ev.optString("type")) {
            case "message_start": {
                JSONObject message = ev.optJSONObject("message");
                if (message != null) usage = copy(message.optJSONObject("usage"));
                break;
            }
            case "content_block_start": {
                int index = ev.getInt("index");
                JSONObject block = copy(ev.optJSONObject("content_block"));
                if (block == null) block = new JSONObject();
                String type = block.optString("type");
                if ("text".equals(type) && !block.has("text")) block.put("text", "");
                if ("thinking".equals(type) && !block.has("thinking")) block.put("thinking", "");
                if (type.endsWith("tool_use")) partialJson.put(index, new StringBuilder());
                content.put(index, block);
                break;
            }
            case "content_block_delta": {
                int index = ev.getInt("index");
                JSONObject block = content.optJSONObject(index);
                JSONObject delta = ev.optJSONObject("delta");
                if (block == null || delta == null) break;
                switch (delta.optString("type")) {
                    case "text_delta": {
                        String piece = delta.optString("text");
                        block.put("text", block.optString("text") + piece);
                        if ("text".equals(block.optString("type")) && sink != null && !piece.isEmpty()) sink.text(piece);
                        break;
                    }
                    case "thinking_delta":
                        block.put("thinking", block.optString("thinking") + delta.optString("thinking"));
                        break;
                    case "signature_delta":
                        block.put("signature", delta.optString("signature"));
                        break;
                    case "input_json_delta": {
                        StringBuilder sb = partialJson.get(index);
                        if (sb != null) sb.append(delta.optString("partial_json"));
                        break;
                    }
                    case "citations_delta": {
                        JSONArray citations = block.optJSONArray("citations");
                        if (citations == null) {
                            citations = new JSONArray();
                            block.put("citations", citations);
                        }
                        JSONObject citation = delta.optJSONObject("citation");
                        if (citation != null) citations.put(citation);
                        break;
                    }
                    default:
                        break; // unbekannte Art: Block bleibt, wie er begann
                }
                break;
            }
            case "content_block_stop": {
                int index = ev.getInt("index");
                StringBuilder sb = partialJson.remove(index);
                JSONObject block = content.optJSONObject(index);
                if (sb != null && block != null && sb.toString().trim().length() > 0) {
                    block.put("input", new JSONObject(sb.toString()));
                }
                break;
            }
            case "message_delta": {
                JSONObject delta = ev.optJSONObject("delta");
                if (delta != null && !delta.isNull("stop_reason")) stopReason = delta.optString("stop_reason", "");
                JSONObject u = ev.optJSONObject("usage");
                if (u != null) {
                    if (usage == null) usage = new JSONObject();
                    for (java.util.Iterator<String> it = u.keys(); it.hasNext(); ) {
                        String k = it.next();
                        usage.put(k, u.get(k));
                    }
                }
                break;
            }
            case "message_stop":
                stopped = true;
                break;
            case "error": {
                JSONObject err = ev.optJSONObject("error");
                errorType = err == null ? "api_error" : err.optString("type", "api_error");
                errorMessage = err == null ? "" : err.optString("message", "");
                break;
            }
            default:
                break; // ping und Unbekanntes
        }
    }

    /** Alle Inhaltsblöcke in Reihenfolge, wie sie eine Antwort ohne Streaming enthielte. */
    JSONArray content() {
        JSONArray out = new JSONArray();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block != null) out.put(block);
        }
        return out;
    }

    String stopReason() {
        return stopReason;
    }

    JSONObject usage() {
        return usage;
    }

    /** Fehler mitten im Strom (z. B. überlastet) – Typ wie in der Fehlerantwort, sonst null. */
    String errorType() {
        return errorType;
    }

    String errorMessage() {
        return errorMessage;
    }

    /** Kam „message_stop“? Sonst ist die Verbindung mittendrin abgerissen. */
    boolean complete() {
        return stopped;
    }

    private static JSONObject copy(JSONObject o) throws JSONException {
        return o == null ? null : new JSONObject(o.toString());
    }
}
