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
 * Der API-Schlüssel wird bewusst NICHT gespeichert – die Datei soll gefahrlos herumliegen können.
 */
final class Backup {
    static final String FILE_NAME = "jarvis-sicherung.json";

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
