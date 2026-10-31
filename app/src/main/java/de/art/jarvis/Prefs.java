package de.art.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Einstellungen: API-Schlüssel, Modell, TFT-Modus. Liegen nur privat auf dem Handy. */
public final class Prefs {
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";
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
        return isGemini() ? DEFAULT_GEMINI_MODEL : DEFAULT_MODEL;
    }

    public void setModel(String value) {
        sp.edit().putString("model", value == null ? "" : value.trim()).apply();
    }

    public boolean tftMode() {
        return sp.getBoolean("tft_mode", false);
    }

    public void setTftMode(boolean value) {
        sp.edit().putBoolean("tft_mode", value).apply();
    }
}
