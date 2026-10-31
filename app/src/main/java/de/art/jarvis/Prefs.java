package de.art.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Einstellungen. Liegen nur privat auf dem Handy und überstehen Neustarts. */
public final class Prefs {
    /** Alltag: Haiku 5.5 – sehr günstig. TFT: Sonnet 5.5 – stärker bei Bildern und Strategie. */
    public static final String DEFAULT_MODEL = "claude-haiku-5-5";
    public static final String TFT_MODEL = "claude-sonnet-5-5";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-flash-latest";

    /** Standardstimme: Alan (britisches Englisch, Piper „medium“) aus SherpaTTS, Tempo 0,9. */
    public static final String DEFAULT_VOICE_HINT = "alan";
    public static final float DEFAULT_RATE = 0.9f;
    public static final float DEFAULT_PITCH = 1.0f;
    /** Wartezeit nach einer Sprechpause, bevor Jarvis die Frage abschickt. */
    public static final int DEFAULT_PAUSE_MS = 1800;

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

    // ------------------------------------------------------------ Stimme
    // Neue Schlüssel („…_v2“), damit die neuen Standards auch bei bestehender Installation greifen.

    /** Paketname der Sprachausgabe-Engine; leer = automatisch (SherpaTTS bevorzugt). */
    public String engine() {
        return sp.getString("engine_v2", "");
    }

    public void setEngine(String value) {
        sp.edit().putString("engine_v2", value == null ? "" : value).apply();
    }

    /** Name der gewählten Stimme; leer = automatisch (Alan bevorzugt). */
    public String voiceName() {
        return sp.getString("voice_v2", "");
    }

    public void setVoiceName(String value) {
        sp.edit().putString("voice_v2", value == null ? "" : value).apply();
    }

    public float pitch() {
        return sp.getFloat("pitch_v2", DEFAULT_PITCH);
    }

    public void setPitch(float value) {
        sp.edit().putFloat("pitch_v2", value).apply();
    }

    public float rate() {
        return sp.getFloat("rate_v2", DEFAULT_RATE);
    }

    public void setRate(float value) {
        sp.edit().putFloat("rate_v2", value).apply();
    }

    // ------------------------------------------------------------ Zuhören

    public int pauseMs() {
        return sp.getInt("pause_ms", DEFAULT_PAUSE_MS);
    }

    public void setPauseMs(int value) {
        sp.edit().putInt("pause_ms", value).apply();
    }

    // ------------------------------------------------------------ Gedächtnis

    /** Dauerhaftes Gedächtnis: ein Fakt pro Zeile („- …“). */
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
