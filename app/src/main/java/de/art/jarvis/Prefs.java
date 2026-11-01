package de.art.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Einstellungen. Liegen nur privat auf dem Handy und überstehen Neustarts. */
public final class Prefs {
    /** Alltag: Sonnet 5.5 ohne Nachdenken – schnell und genau. Haiku 5.5 bleibt als Sparoption wählbar. */
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    public static final String BUDGET_MODEL = "claude-haiku-5-5";
    public static final String TFT_MODEL = "claude-sonnet-5-5";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-flash-latest";

    /**
     * Standard-Stimmen für die Handy-Sprachausgabe, in dieser Reihenfolge gesucht:
     * Thorsten (deutsch, männlich) → Miro (deutsch, männlich) → Alan (britisch, Notlösung).
     * Premium (ElevenLabs „Daniel“) hat Vorrang, sobald ein ElevenLabs-Schlüssel eingetragen ist.
     */
    public static final String[] DEFAULT_VOICE_HINTS = {"thorsten", "miro", "alan"};
    public static final float DEFAULT_RATE = 0.9f;
    public static final float DEFAULT_PITCH = 1.0f;
    /** Wartezeit nach einer Sprechpause, bevor Jarvis die Frage abschickt. */
    public static final int DEFAULT_PAUSE_MS = 1200;

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE);
    }

    public String apiKey() {
        return sp.getString("api_key", "").trim();
    }

    public void setApiKey(String value) {
        sp.edit().putString("api_key", Text.cleanKey(value)).apply();
    }

    public String apiKeyPreview() {
        return Text.keyPreview(apiKey());
    }

    /** Google-Schlüssel beginnen mit „AIza“, Anthropic-Schlüssel mit „sk-ant-“. */
    public boolean isGemini() {
        return Text.isGeminiKey(apiKey());
    }

    /** Eingetragenes Modell oder leer. */
    public String rawModel() {
        return sp.getString("model", "").trim();
    }

    public static boolean isValidModel(String m) {
        return Text.isValidModel(m);
    }

    public void setModel(String value) {
        sp.edit().putString("model", value == null ? "" : value.trim()).apply();
    }

    /** Eingetragenes Modell oder der passende Standard für den Anbieter. */
    public String model() {
        String m = rawModel();
        if (isValidModel(m)) return m;
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

    /** Name der gewählten Stimme; leer = automatisch (siehe DEFAULT_VOICE_HINTS). */
    public String voiceName() {
        return sp.getString("voice_v3", "");
    }

    public void setVoiceName(String value) {
        sp.edit().putString("voice_v3", value == null ? "" : value).apply();
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
        return sp.getInt("pause_ms_v2", DEFAULT_PAUSE_MS);
    }

    public void setPauseMs(int value) {
        sp.edit().putInt("pause_ms_v2", value).apply(); // v2: neue Bedeutung, einmalig auf 1,2 s zurückgesetzt
    }

    // ------------------------------------------------------------ Gedächtnis

    /** Dauerhaftes Gedächtnis: ein Fakt pro Zeile („- …“). */
    public String memory() {
        return sp.getString("memory", "").trim();
    }

    public void setMemory(String value) {
        sp.edit().putString("memory", value == null ? "" : value.trim()).apply();
    }

    // ------------------------------------------------------------ Premium-Stimme (ElevenLabs, optional)

    public String elevenKey() {
        return sp.getString("eleven_key", "");
    }

    public void setElevenKey(String value) {
        sp.edit().putString("eleven_key", Text.cleanKey(value)).apply();
    }

    /** Stimmen-ID; leer = Standard („Daniel“, britisch, ruhig). */
    public String elevenVoice() {
        String v = sp.getString("eleven_voice", "").trim();
        return v.isEmpty() ? ElevenTts.DEFAULT_VOICE : v;
    }

    public String rawElevenVoice() {
        return sp.getString("eleven_voice", "").trim();
    }

    public void setElevenVoice(String value) {
        sp.edit().putString("eleven_voice", value == null ? "" : value.trim()).apply();
    }

    /** Ort der automatischen Sicherungsdatei (vom Nutzer gewählt); leer = keine Sicherung. */
    public String backupUri() {
        return sp.getString("backup_uri", "");
    }

    public void setBackupUri(String value) {
        sp.edit().putString("backup_uri", value == null ? "" : value).apply();
    }

    /** Spielmodus (TFT): nur Sprache, nichts verdeckt das Spielbild, TFT-Coach-Anweisungen. */
    /** Schlüssel mit in die Sicherungsdatei schreiben (Standard: aus). */
    public boolean backupKeys() {
        return sp.getBoolean("backup_keys", false);
    }

    public void setBackupKeys(boolean value) {
        sp.edit().putBoolean("backup_keys", value).apply();
    }

    public boolean tftMode() {
        return sp.getBoolean("tft_mode", false);
    }

    public void setTftMode(boolean value) {
        sp.edit().putBoolean("tft_mode", value).apply();
    }

    // ------------------------------------------------------------ Freihändig („Hey Jarvis“)

    public static final boolean DEFAULT_WAKE_WORD = true;
    /** 1 = reagiert nur auf sehr deutliches „Hey Jarvis“, 10 = reagiert auf fast alles. */
    public static final int DEFAULT_SENSITIVITY = 5;

    public boolean wakeWord() {
        return sp.getBoolean("wake_word", DEFAULT_WAKE_WORD);
    }

    public void setWakeWord(boolean value) {
        sp.edit().putBoolean("wake_word", value).apply();
    }

    public int sensitivity() {
        return clamp(sp.getInt("wake_sensitivity", DEFAULT_SENSITIVITY), 1, 10);
    }

    public void setSensitivity(int value) {
        sp.edit().putInt("wake_sensitivity", clamp(value, 1, 10)).apply();
    }

    /** Erkennungsschwelle: Stufe 5 = 0,5 (Standard von openWakeWord), Stufe 10 = 0,1, Stufe 1 = 0,82. */
    public static float thresholdFor(int sensitivity) {
        return 0.9f - clamp(sensitivity, 1, 10) * 0.08f;
    }

    /** Kurzer Ton, sobald Jarvis zuhört – wichtig, wenn nichts auf dem Bildschirm erscheint. */
    public boolean earcon() {
        return sp.getBoolean("earcon", true);
    }

    public void setEarcon(boolean value) {
        sp.edit().putBoolean("earcon", value).apply();
    }

    // ------------------------------------------------------------ Anzeige

    /** Schwebendes J über anderen Apps. Standard: aus. Im Spielmodus immer ausgeblendet. */
    public boolean floatingJ() {
        return sp.getBoolean("floating_j", false);
    }

    public void setFloatingJ(boolean value) {
        sp.edit().putBoolean("floating_j", value).apply();
    }

    public int bubbleX(int fallback) {
        return sp.getInt("bubble_x", fallback);
    }

    public int bubbleY(int fallback) {
        return sp.getInt("bubble_y", fallback);
    }

    public void setBubblePosition(int x, int y) {
        sp.edit().putInt("bubble_x", x).putInt("bubble_y", y).apply();
    }

    /** Untertitel am Bildschirmrand im Alltag (nicht anklickbar, verschwinden von selbst). */
    public boolean subtitles() {
        return sp.getBoolean("subtitles", true);
    }

    public void setSubtitles(boolean value) {
        sp.edit().putBoolean("subtitles", value).apply();
    }

    /** Untertitel auch im Spielmodus – Standard aus, dort antwortet Jarvis nur per Sprache. */
    public boolean subtitlesInGame() {
        return sp.getBoolean("subtitles_game", false);
    }

    public void setSubtitlesInGame(boolean value) {
        sp.edit().putBoolean("subtitles_game", value).apply();
    }

    /** Untertitel oben (Standard) oder unten am Bildschirmrand. */
    public boolean subtitlesTop() {
        return sp.getBoolean("subtitles_top", true);
    }

    public void setSubtitlesTop(boolean value) {
        sp.edit().putBoolean("subtitles_top", value).apply();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
