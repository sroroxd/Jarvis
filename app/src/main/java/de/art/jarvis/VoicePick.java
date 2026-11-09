package de.art.jarvis;

/**
 * Welche Stimme einen Satz spricht. Reine Entscheidung ohne Android, damit sie in den automatischen
 * Tests läuft (wie {@link SpeechGate}, {@link Costs}).
 *
 * <p>Wunsch #62 „Gleichbleibende Stimme“: Die Stimme wechselte mitten im Gespräch, weil ElevenLabs bei
 * jedem Fehler auf die Handy-Stimme zurückfiel, es beim nächsten Satz aber wieder mit ElevenLabs
 * versuchte – klappte es mal, mal nicht, sprang die Stimme hin und her. Die Offline-Stimme (Pocket) hatte
 * dafür längst eine Sperre ({@code pocketFailed}), ElevenLabs nicht. Diese Klasse hält das symmetrisch:
 * Sobald eine bevorzugte Stimme in diesem Gespräch einmal versagt hat, bleibt es bei der Handy-Stimme,
 * bis ein neues Gespräch beginnt und die Sperre zurückgesetzt wird.
 */
final class VoicePick {

    enum Speaker {
        /** Offline-Stimme (Pocket TTS). */
        POCKET,
        /** Premium-Stimme (ElevenLabs). */
        PREMIUM,
        /** Stimme des Handys (System-Sprachausgabe). */
        DEVICE
    }

    private VoicePick() {}

    /**
     * @param pocketUsable     Art hat die Offline-Stimme gewählt, das Modell liegt bereit und sie hat nicht versagt.
     * @param elevenConfigured ein ElevenLabs-Schlüssel ist hinterlegt.
     * @param elevenFailed     ElevenLabs hat in diesem Gespräch schon einmal versagt.
     */
    static Speaker choose(boolean pocketUsable, boolean elevenConfigured, boolean elevenFailed) {
        if (pocketUsable) return Speaker.POCKET;
        if (elevenConfigured && !elevenFailed) return Speaker.PREMIUM;
        return Speaker.DEVICE;
    }
}
