package de.art.jarvis.wake;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Prüft die Wachwort-Erkennung mit echten Aufnahmen (16 kHz, gesprochen von einer Piper-Stimme).
 * Referenzwerte der Python-Vorlage: „Hey Jarvis“ 0,998 · normale Sprache 0,000 · „Hey Travis“ 0,050.
 */
public class WakeWordDetectorTest {
    private static WakeWordDetector detector;

    @BeforeClass
    public static void load() throws Exception {
        Path dir = Paths.get(System.getProperty("jarvis.models", "../app/src/main/assets/wakeword"));
        detector = new WakeWordDetector(
                Files.readAllBytes(dir.resolve("melspectrogram.onnx")),
                Files.readAllBytes(dir.resolve("embedding_model.onnx")),
                Files.readAllBytes(dir.resolve("hey_jarvis_v0.1.onnx")));
    }

    @AfterClass
    public static void close() {
        if (detector != null) detector.close();
    }

    @Test
    public void heyJarvisWirdErkannt() throws Exception {
        float max = maxScore(readWav("hey_jarvis.wav"));
        System.out.println("Hey Jarvis: " + max);
        assertTrue("„Hey Jarvis“ nicht erkannt (" + max + ")", max > 0.9f);
    }

    @Test
    public void normaleSpracheLoestNichtAus() throws Exception {
        float max = maxScore(readWav("negative_speech.wav"));
        System.out.println("Normale Sprache: " + max);
        assertTrue("Fehlauslöser bei normaler Sprache (" + max + ")", max < 0.2f);
    }

    @Test
    public void aehnlichesWortLoestNichtAus() throws Exception {
        float max = maxScore(readWav("hey_travis.wav"));
        System.out.println("Hey Travis: " + max);
        assertTrue("Fehlauslöser bei „Hey Travis“ (" + max + ")", max < 0.3f);
    }

    @Test
    public void stilleLoestNichtAus() throws Exception {
        float max = maxScore(new short[WakeWordDetector.SAMPLE_RATE * 3]);
        assertTrue("Fehlauslöser bei Stille (" + max + ")", max < 0.1f);
    }

    /** Wie im Livebetrieb: eine Sekunde Stille davor und danach, dann blockweise verarbeiten. */
    private static float maxScore(short[] clip) throws Exception {
        int pad = WakeWordDetector.SAMPLE_RATE;
        short[] audio = new short[pad + clip.length + pad];
        System.arraycopy(clip, 0, audio, pad, clip.length);
        detector.reset();
        float max = 0f;
        short[] chunk = new short[WakeWordDetector.CHUNK];
        for (int i = 0; i + WakeWordDetector.CHUNK < audio.length; i += WakeWordDetector.CHUNK) {
            System.arraycopy(audio, i, chunk, 0, WakeWordDetector.CHUNK);
            max = Math.max(max, detector.process(chunk));
        }
        return max;
    }

    /** Liest die Samples einer 16-bit-Mono-WAV-Datei (sucht den „data“-Abschnitt). */
    private static short[] readWav(String name) throws Exception {
        byte[] bytes;
        try (InputStream in = WakeWordDetectorTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("Testdatei fehlt: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            bytes = out.toByteArray();
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        while (pos + 8 <= bytes.length) {
            String id = new String(bytes, pos, 4, "US-ASCII");
            int size = bb.getInt(pos + 4);
            if ("data".equals(id)) {
                int count = Math.min(size, bytes.length - pos - 8) / 2;
                short[] samples = new short[count];
                bb.position(pos + 8);
                bb.asShortBuffer().get(samples);
                return samples;
            }
            pos += 8 + size + (size & 1);
        }
        throw new IllegalStateException("Kein data-Abschnitt in " + name);
    }
}
