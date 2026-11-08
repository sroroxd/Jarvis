package de.art.jarvis;

import android.content.Context;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Sicherungsdatei für Gedächtnis und Einstellungen. Liegt an einem Ort, den Art selbst wählt
 * (z. B. „Downloads“), und übersteht damit auch eine Deinstallation.
 * API-Schlüssel (Claude/Gemini und ElevenLabs) werden bewusst NICHT gespeichert – die Datei soll gefahrlos herumliegen können.
 */
final class Backup {
    /** Die Test-App sichert in eine eigene Datei, damit sie die Sicherung der normalen App nie überschreibt. */
    static final String FILE_NAME = BuildConfig.TEST_APP ? "jarvis-test-sicherung.json" : "jarvis-sicherung.json";

    private Backup() {}

    static String toJson(Prefs p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("format", 1);
        o.put("gespeichert", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY).format(new Date()));
        o.put("memory", p.memory());
        o.put("model", p.rawModel());
        o.put("tft", p.tftMode());
        o.put("engine", p.engine());
        o.put("voice", p.voiceName());
        o.put("rate", (double) p.rate());
        o.put("pitch", (double) p.pitch());
        o.put("pause", p.pauseMs());
        o.put("elevenVoice", p.rawElevenVoice());
        if (p.backupKeys()) { // nur auf ausdrücklichen Wunsch
            o.put("apiKey", p.apiKey());
            o.put("elevenKey", p.elevenKey());
            o.put("agentToken", p.agentToken());
            o.put("githubToken", p.githubToken());
        }
        o.put("agent", p.agentEnabled());
        o.put("wishes", p.wishes());
        o.put("wakeWord", p.wakeWord());
        o.put("sensitivity", p.sensitivity());
        o.put("earcon", p.earcon());
        o.put("bargeIn", p.bargeIn());
        o.put("bargeInSensitivity", p.bargeInSensitivity());
        o.put("gameAuto", p.gameAuto());
        o.put("echoCallMode", p.echoCallMode());
        o.put("gameThinkDeep", p.gameThinkDeep());
        o.put("proactiveCoach", p.proactiveCoach());
        o.put("floatingJ", p.floatingJ());
        o.put("subtitles", p.subtitles());
        o.put("subtitlesGame", p.subtitlesInGame());
        o.put("subtitlesTop", p.subtitlesTop());
        return o.toString(2);
    }

    /** Übernimmt eine Sicherung. Gibt die Zahl der wiederhergestellten Gedächtniseinträge zurück. */
    static int apply(Prefs p, String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        p.setMemory(o.optString("memory", ""));
        p.setModel(o.optString("model", ""));
        p.setTftMode(o.optBoolean("tft", false));
        p.setEngine(o.optString("engine", ""));
        p.setVoiceName(o.optString("voice", ""));
        p.setRate((float) o.optDouble("rate", Prefs.DEFAULT_RATE));
        p.setPitch((float) o.optDouble("pitch", Prefs.DEFAULT_PITCH));
        p.setPauseMs(o.optInt("pause", Prefs.DEFAULT_PAUSE_MS));
        p.setElevenVoice(o.optString("elevenVoice", ""));
        String apiKey = o.optString("apiKey", "");
        if (!apiKey.isEmpty()) {
            p.setApiKey(apiKey);
            p.setBackupKeys(true);
        }
        String elevenKey = o.optString("elevenKey", "");
        if (!elevenKey.isEmpty()) p.setElevenKey(elevenKey);
        String agentToken = o.optString("agentToken", "");
        if (!agentToken.isEmpty()) p.setAgentToken(agentToken);
        p.setAgentEnabled(o.optBoolean("agent", false));
        String githubToken = o.optString("githubToken", "");
        if (!githubToken.isEmpty()) p.setGithubToken(githubToken);
        if (o.has("wishes")) p.setWishes(o.optString("wishes", ""));
        p.setWakeWord(o.optBoolean("wakeWord", Prefs.DEFAULT_WAKE_WORD));
        p.setSensitivity(o.optInt("sensitivity", Prefs.DEFAULT_SENSITIVITY));
        p.setEarcon(o.optBoolean("earcon", true));
        p.setBargeIn(o.optBoolean("bargeIn", true));
        p.setBargeInSensitivity(o.optInt("bargeInSensitivity", SpeechGate.DEFAULT_LEVEL));
        p.setGameAuto(o.optBoolean("gameAuto", false));
        p.setEchoCallMode(o.optBoolean("echoCallMode", true));
        p.setGameThinkDeep(o.optBoolean("gameThinkDeep", false));
        p.setProactiveCoach(o.optBoolean("proactiveCoach", true));
        p.setFloatingJ(o.optBoolean("floatingJ", false));
        p.setSubtitles(o.optBoolean("subtitles", true));
        p.setSubtitlesInGame(o.optBoolean("subtitlesGame", false));
        p.setSubtitlesTop(o.optBoolean("subtitlesTop", true));
        return Memory.load(p.memory()).size();
    }

    static boolean write(Context c, Prefs p, Uri uri) {
        try (OutputStream os = c.getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) return false;
            os.write(toJson(p).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String read(Context c, Uri uri) throws java.io.IOException {
        try (InputStream is = c.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new java.io.IOException("Datei nicht lesbar");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    /** Schreibt still in die eingerichtete Sicherungsdatei – nach jeder Änderung aufgerufen. */
    static void autoSave(Context c, Prefs p) {
        String u = p.backupUri();
        if (!u.isEmpty()) write(c, p, Uri.parse(u));
    }
}
