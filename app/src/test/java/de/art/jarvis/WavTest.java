package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Sprachprobe der Offline-Stimme einlesen. */
public class WavTest {

    private static byte[] wav(int format, int channels, int rate, int bits, byte[] extraChunk, ByteBuffer data) {
        int dataLen = data.position();
        int extra = extraChunk == null ? 0 : extraChunk.length;
        ByteBuffer b = ByteBuffer.allocate(44 + extra + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(36 + extra + dataLen).putInt(0x45564157);
        b.putInt(0x20746d66).putInt(16).putShort((short) format).putShort((short) channels).putInt(rate)
                .putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8)).putShort((short) bits);
        if (extraChunk != null) b.put(extraChunk);
        b.putInt(0x61746164).putInt(dataLen);
        b.put(data.array(), 0, dataLen);
        return b.array();
    }

    @Test
    public void pcm16Mono() throws IOException {
        ByteBuffer d = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
        d.putShort((short) 0).putShort((short) 16384).putShort((short) -32768);
        Wav w = Wav.parse(wav(1, 1, 24000, 16, null, d));
        assertEquals(24000, w.rate);
        assertEquals(3, w.samples.length);
        assertEquals(0.5f, w.samples[1], 1e-6);
        assertEquals(-1f, w.samples[2], 1e-6);
    }

    /** Stereo wird gemittelt; ein unbekannter Abschnitt („LIST“) vor den Daten wird übersprungen. */
    @Test
    public void floatStereoMitZusatzabschnitt() throws IOException {
        ByteBuffer d = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        d.putFloat(0.2f).putFloat(0.4f).putFloat(-1f).putFloat(1f);
        byte[] list = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x5453494c).putInt(4).putInt(0).array();
        Wav w = Wav.parse(wav(3, 2, 32000, 32, list, d));
        assertEquals(32000, w.rate);
        assertEquals(2, w.samples.length);
        assertEquals(0.3f, w.samples[0], 1e-6);
        assertEquals(0f, w.samples[1], 1e-6);
    }

    @Test
    public void keinWav() {
        try {
            Wav.parse("hallo welt, kein wav".getBytes());
            fail("erwartet IOException");
        } catch (IOException expected) {
            // richtig
        }
    }
}
