package de.art.jarvis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Wunsch #62: Die Stimme bleibt im Gespräch gleich, statt bei ElevenLabs-Fehlern hin- und herzuspringen. */
public class VoicePickTest {

    @Test
    public void offlineStimmeHatVorrang() {
        // Pocket gewählt und einsatzbereit: immer Pocket, egal was sonst eingerichtet ist.
        assertEquals(VoicePick.Speaker.POCKET, VoicePick.choose(true, true, false));
        assertEquals(VoicePick.Speaker.POCKET, VoicePick.choose(true, false, false));
        assertEquals(VoicePick.Speaker.POCKET, VoicePick.choose(true, true, true));
    }

    @Test
    public void premiumSolangeElevenNichtVersagtHat() {
        assertEquals(VoicePick.Speaker.PREMIUM, VoicePick.choose(false, true, false));
    }

    @Test
    public void nachElevenFehlerBleibtEsBeiDerHandyStimme() {
        // Der Kern des Wunsches: Einmal versagt → für den Rest des Gesprächs Handy-Stimme, kein Zurückspringen.
        assertEquals(VoicePick.Speaker.DEVICE, VoicePick.choose(false, true, true));
    }

    @Test
    public void ohneEinrichtungDieHandyStimme() {
        assertEquals(VoicePick.Speaker.DEVICE, VoicePick.choose(false, false, false));
        assertEquals(VoicePick.Speaker.DEVICE, VoicePick.choose(false, false, true));
    }
}
