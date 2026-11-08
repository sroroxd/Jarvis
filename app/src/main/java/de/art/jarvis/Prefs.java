package de.art.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Einstellungen. Liegen nur privat auf dem Handy und überstehen Neustarts. */
public final class Prefs {
    /** Alltag: Sonnet 5.5, Nachdenken nur bei Bedarf (Stufe „low“) – schnell und genau. Haiku 5.5 bleibt als Sparoption wählbar. */
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    public static final String BUDGET_MODEL = "claude-haiku-5-5";
    public static final String TFT_MODEL = "claude-sonnet-5-5";

    /**
     * Standard-Stimmen für die Handy-Sprachausgabe, in dieser Reihenfolge gesucht:
     * Miro (deutsch, männlich – Arts Wahl) → Thorsten (deutsch, männlich) → Alan (britisch, Notlösung).
     * Premium (ElevenLabs „Daniel“) hat Vorrang, sobald ein ElevenLabs-Schlüssel eingetragen ist.
     */
    public static final String[] DEFAULT_VOICE_HINTS = {"miro", "thorsten", "alan"};
    public static final float DEFAULT_RATE = 0.95f;
    public static final float DEFAULT_PITCH = 0.9f;
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

    /** Eingetragenes Modell oder der Standard. */
    public String model() {
        String m = rawModel();
        if (isValidModel(m)) return m;
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

    // ------------------------------------------------------------ Kosten (Tagessumme aller Claude-Anfragen)

    private static final Object COST_LOCK = new Object();

    /** Zählt eine Anfrage zur Tagessumme; ein neuer Tag beginnt bei null. Aus jedem Thread aufrufbar. */
    public void addCost(String day, double usd) {
        synchronized (COST_LOCK) {
            double sum = costToday(day) + usd;
            sp.edit().putString("cost_day", day).putFloat("cost_usd", (float) sum).apply();
        }
    }

    /** Ausgaben heute in US-Dollar (Fragen, ungefragte Ansagen, Phasen-Wächter, Meta-Recherche). */
    public double costToday(String day) {
        return day.equals(sp.getString("cost_day", "")) ? sp.getFloat("cost_usd", 0f) : 0;
    }

    // ------------------------------------------------------------ TFT-Meta aus dem Internet (täglich)

    public String metaText() {
        return sp.getString("meta_text", "");
    }

    /** Datum der letzten Meta-Recherche (yyyy-MM-dd); leer = noch nie. */
    public String metaDate() {
        return sp.getString("meta_date", "");
    }

    public void setMeta(String text, String date) {
        sp.edit().putString("meta_text", text == null ? "" : text).putString("meta_date", date == null ? "" : date).apply();
    }

    public boolean tftMode() {
        return sp.getBoolean("tft_mode", false);
    }

    public void setTftMode(boolean value) {
        sp.edit().putBoolean("tft_mode", value).apply();
    }

    // ------------------------------------------------------------ Zuhören (Wunsch #39/#47: einfach losreden)

    /**
     * Immer zuhören: Solange Jarvis läuft und der Bildschirm an ist, hört er durchgehend zu – ohne Wachwort und ohne
     * Antippen. Standard an (Arts Wunsch #47, „Wachwort entfernen“).
     */
    public boolean alwaysListen() {
        return sp.getBoolean("always_listen", true);
    }

    public void setAlwaysListen(boolean value) {
        sp.edit().putBoolean("always_listen", value).apply();
    }

    /** 1 = reagiert nur auf sehr deutliches „Jarvis“, 10 = reagiert auf fast alles (gilt fürs Unterbrechen). */
    public static final int DEFAULT_SENSITIVITY = 5;

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

    // ---------------------------------------------------------------- Dazwischenreden

    /** Im Dauergespräch unterbricht Arts Stimme Jarvis sofort (Standard an). */
    /** Jarvis spricht mit der Offline-Stimme (Pocket TTS) statt mit ElevenLabs oder der Handy-Stimme. */
    public boolean pocketVoice() {
        return sp.getBoolean("pocket_voice", false);
    }

    public void setPocketVoice(boolean value) {
        sp.edit().putBoolean("pocket_voice", value).apply();
    }

    public boolean bargeIn() {
        return sp.getBoolean("barge_in", true);
    }

    public void setBargeIn(boolean value) {
        sp.edit().putBoolean("barge_in", value).apply();
    }

    /**
     * Im Dauergespräch wie bei einem Videoanruf abspielen (Telefonmodus über den Lautsprecher), damit die
     * Echo-Unterdrückung des Handys Jarvis' Stimme herausfiltert. Standard an.
     */
    public boolean echoCallMode() {
        return sp.getBoolean("echo_call_mode", true);
    }

    public void setEchoCallMode(boolean value) {
        sp.edit().putBoolean("echo_call_mode", value).apply();
    }

    /** Wie leicht Arts Stimme Jarvis unterbricht: 1 = schwer, 10 = sehr leicht (Wunsch #25). */
    public int bargeInSensitivity() {
        return clamp(sp.getInt("barge_in_sensitivity", SpeechGate.DEFAULT_LEVEL), 1, 10);
    }

    public void setBargeInSensitivity(int value) {
        sp.edit().putInt("barge_in_sensitivity", clamp(value, 1, 10)).apply();
    }

    // ---------------------------------------------------------------- Spielerkennung

    /** TFT im Vordergrund → Spielmodus an und ohne Wachwort zuhören. Braucht den Nutzungsdatenzugriff. */
    public boolean gameAuto() {
        return sp.getBoolean("game_auto", false);
    }

    public void setGameAuto(boolean value) {
        sp.edit().putBoolean("game_auto", value).apply();
    }

    /** Spielmodus: zu Beginn jeder Planungsphase von selbst ansagen (Wunsch #41/#42). Standard an. */
    public boolean proactiveCoach() {
        return sp.getBoolean("proactive_coach", true);
    }

    public void setProactiveCoach(boolean value) {
        sp.edit().putBoolean("proactive_coach", value).apply();
    }

    /** Spielmodus: gründlicher nachdenken (langsamer). Standard aus – Tempo zuerst (Wunsch #23). */
    public boolean gameThinkDeep() {
        return sp.getBoolean("game_think_deep", false);
    }

    public void setGameThinkDeep(boolean value) {
        sp.edit().putBoolean("game_think_deep", value).apply();
    }

    // ---------------------------------------------------------------- Wünsche an die Entwicklung

    /** GitHub-Schlüssel (fine-grained, nur sroroxd/Jarvis, „Issues: Read and write“). */
    public String githubToken() {
        return sp.getString("github_token", "").trim();
    }

    public void setGithubToken(String value) {
        sp.edit().putString("github_token", Text.cleanKey(value)).apply();
    }

    public String githubTokenPreview() {
        return Text.keyPreview(githubToken());
    }

    /** Notierte Wünsche, Format siehe {@link Wishes}. */
    public String wishes() {
        return sp.getString("wishes", "");
    }

    public void setWishes(String value) {
        sp.edit().putString("wishes", value == null ? "" : value).apply();
    }

    // ---------------------------------------------------------------- Agent-Rückgrat

    /** Standard-Port des Agenten in Termux (siehe {@code agent/jarvis-agent.mjs}). */
    public static final int DEFAULT_AGENT_PORT = 8777;

    /**
     * Aufträge an den Agenten in Termux weitergeben. Aus, solange Art den Agenten
     * nicht eingerichtet hat – sonst liefe jeder Auftrag in einen Verbindungsfehler.
     */
    public boolean agentEnabled() {
        return sp.getBoolean("agent_enabled", false);
    }

    public void setAgentEnabled(boolean value) {
        sp.edit().putBoolean("agent_enabled", value).apply();
    }

    public int agentPort() {
        return clamp(sp.getInt("agent_port", DEFAULT_AGENT_PORT), 1024, 65535);
    }

    /** Token des Agenten – steht auf dem Handy in {@code ~/.jarvis-agent/token}. */
    public String agentToken() {
        return sp.getString("agent_token", "").trim();
    }

    public void setAgentToken(String value) {
        sp.edit().putString("agent_token", value == null ? "" : value.trim()).apply();
    }

    /** Vorschau fürs Anzeigen, ohne das ganze Token zu zeigen. */
    public String agentTokenPreview() {
        return Text.keyPreview(agentToken());
    }

    /** Der Agent ist nur nutzbar, wenn er eingeschaltet ist und ein Token vorliegt. */
    public boolean agentReady() {
        return agentEnabled() && !agentToken().isEmpty();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
