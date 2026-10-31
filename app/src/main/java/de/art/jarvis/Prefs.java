package de.art.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Einstellungen: API-Schlüssel, Modell, TFT-Modus. Liegen nur privat auf dem Handy. */
public final class Prefs {
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

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

    public String model() {
        String m = sp.getString("model", "").trim();
        return m.isEmpty() ? DEFAULT_MODEL : m;
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
