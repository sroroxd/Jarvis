package de.art.jarvis.neubau;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class AudioProbeTest {
    @Test public void echoesSpeechWithoutExternalApi() {
        assertEquals("Sprachtest erfolgreich. Du hast gesagt: Hallo Jarvis",
                AudioProbe.reply("  Hallo Jarvis  "));
    }

    @Test public void emptyAudioIsSafe() {
        assertEquals("Ich habe nichts verstanden.", AudioProbe.reply(null));
        assertEquals("Ich habe nichts verstanden.", AudioProbe.reply("   "));
    }
}
