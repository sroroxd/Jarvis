package de.art.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Einstellungen: API-Schlüssel, Modell, TFT-Modus. Liegen nur privat auf dem Handy. */
public final class Prefs {
    /** Alltag: Haiku 5.5 – sehr günstig. TFT: Sonnet 5.5 – stärker bei Bildern und Strategie. */
    public static final String DEFAULT_MODEL = "claude-haiku-5-5";
    public static final String TFT_MODEL = "claude-sonnet-5-5";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-flash-latest";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE);
    }

    public String apiKey() {
        return sp.getString("api_key", "").trim();
    }

    public void setApiKey(String value) {
        sp.edit().putString("api_key", value == null ? "" : value.trim()).apply();
    }

    /** Google-Schlüssel beginnen mit „AIza“, Anthropic-Schlüssel mit „sk-ant-“. */
    public boolean isGemini() {
        return apiKey().startsWith("AIza");
    }

    /** Eingetragenes Modell oder leer. */
    public String rawModel() {
        return sp.getString("model", "").trim();
    }

    /** Eingetragenes Modell oder der passende Standard für den Anbieter. */
    public String model() {
        String m = rawModel();
        if (!m.isEmpty()) return m;
        if (isGemini()) return DEFAULT_GEMINI_MODEL;
        return tftMode() ? TFT_MODEL : DEFAULT_MODEL;
    }

    public void setModel(String value) {
        sp.edit().putString("model", value == null ? "" : value.trim()).apply();
    }

    public String voiceName() {
        return sp.getString("voice", "");
    }

    public void setVoiceName(String value) {
        sp.edit().putString("voice", value == null ? "" : value).apply();
    }

    /** Tonhöhe: 1.0 = normal, kleiner = tiefer. Standard leicht tiefer für den Jarvis-Klang. */
    public float pitch() {
        return sp.getFloat("pitch", 0.9f);
    }

    public void setPitch(float value) {
        sp.edit().putFloat("pitch", value).apply();
    }

    public float rate() {
        return sp.getFloat("rate", 1.0f);
    }

    public void setRate(float value) {
        sp.edit().putFloat("rate", value).apply();
    }

    /** Dauerhaftes Gedächtnis: ein Fakt pro Zeile. */
    public String memory() {
        return sp.getString("memory", "").trim();
    }

    public void setMemory(String value) {
        sp.edit().putString("memory", value == null ? "" : value.trim()).apply();
    }

    public boolean tftMode() {
        return sp.getBoolean("tft_mode", false);
    }

    public void setTftMode(boolean value) {
        sp.edit().putBoolean("tft_mode", value).apply();
    }
}
