package de.art.jarvis.wake;

import static org.junit.Assert.assertEquals;
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
 * Prüft das Wachwort „Jarvis“ mit Testaufnahmen (16 kHz, Piper-Stimmen, die im Training NIE vorkamen).
 * Referenzwerte der Python-Nachbildung (training/wakeword): jarvis 0,99 · britisch 0,92 · deutsch 0,96 ·
 * im Satz 0,99 · „Hey Jarvis“ 0,99 · „Ja, was …“ 0,00 · „Travis …“ 0,00 · „Hey Travis“ 0,09.
 */
public class WakeWordDetectorTest {
    private static Path models;
    private static WakeWordDetector detector;

    @BeforeClass
    public static void load() throws Exception {
        models = Paths.get(System.getProperty("jarvis.models", "../app/src/main/assets/wakeword"));
        detector = new WakeWordDetector(
                Files.readAllBytes(models.resolve("melspectrogram.onnx")),
                Files.readAllBytes(models.resolve("embedding_model.onnx")),
                Files.readAllBytes(models.resolve("jarvis.onnx")));
    }

    @AfterClass
    public static void close() {
        if (detector != null) detector.close();
    }

    // ------------------------------------------------------------ soll auslösen

    @Test
    public void jarvisWirdErkannt() throws Exception {
        assertAbove("jarvis.wav", 0.8f);
    }

    @Test
    public void britischesJarvisWirdErkannt() throws Exception {
        assertAbove("jarvis_britisch.wav", 0.6f);
    }

    @Test
    public void deutschAusgesprochenesJarvisWirdErkannt() throws Exception {
        assertAbove("jarvis_deutsch.wav", 0.6f);
    }

    @Test
    public void jarvisMitDirektWeitersprechenWirdErkannt() throws Exception {
        assertAbove("jarvis_im_satz.wav", 0.8f);
    }

    @Test
    public void heyJarvisWirdErkannt() throws Exception {
        assertAbove("hey_jarvis.wav", 0.8f);
    }

    // ------------------------------------------------------------ soll NICHT auslösen

    @Test
    public void jaWasLoestNichtAus() throws Exception {
        assertBelow("ja_was.wav", 0.2f);
    }

    @Test
    public void travisLoestNichtAus() throws Exception {
        assertBelow("travis.wav", 0.2f);
        assertBelow("hey_travis.wav", 0.3f);
    }

    @Test
    public void normaleSpracheLoestNichtAus() throws Exception {
        assertBelow("negative_speech.wav", 0.2f);
    }

    @Test
    public void stilleLoestNichtAus() throws Exception {
        float max = maxScore(detector, new short[WakeWordDetector.SAMPLE_RATE * 3]);
        assertTrue("Fehlauslöser bei Stille (" + max + ")", max < 0.1f);
    }

    // ------------------------------------------------------------ mehrere Modelle gleichzeitig

    /** Zwei Köpfe teilen sich die Merkmale; geliefert wird der höhere Wert – nie weniger als mit einem Kopf. */
    @Test
    public void mehrereModelleLiefernDenHoechstenWert() throws Exception {
        Path reference = Paths.get(System.getProperty("jarvis.reference",
                "../training/wakeword/referenz/hey_jarvis_v0.1.onnx"));
        try (WakeWordDetector both = new WakeWordDetector(
                Files.readAllBytes(models.resolve("melspectrogram.onnx")),
                Files.readAllBytes(models.resolve("embedding_model.onnx")),
                Files.readAllBytes(models.resolve("jarvis.onnx")),
                Files.readAllBytes(reference))) {
            short[] clip = readWav("jarvis_britisch.wav");
            float single = maxScore(detector, clip);
            float combined = maxScore(both, clip);
            System.out.println("Ein Modell: " + single + " · zwei Modelle: " + combined);
            assertTrue("Zwei Modelle liefern weniger als eins", combined >= single - 1e-4f);
            assertEquals("Stille mit zwei Modellen", 0f, maxScore(both, new short[WakeWordDetector.SAMPLE_RATE * 3]), 0.1f);
        }
    }

    // ------------------------------------------------------------ Hilfen

    private static void assertAbove(String file, float min) throws Exception {
        float max = maxScore(detector, readWav(file));
        System.out.println(file + ": " + max);
        assertTrue("„" + file + "“ nicht erkannt (" + max + ")", max > min);
    }

    private static void assertBelow(String file, float limit) throws Exception {
        float max = maxScore(detector, readWav(file));
        System.out.println(file + ": " + max);
        assertTrue("Fehlauslöser bei „" + file + "“ (" + max + ")", max < limit);
    }

    /** Wie im Livebetrieb: eine Sekunde Stille davor und danach, dann blockweise verarbeiten. */
    private static float maxScore(WakeWordDetector d, short[] clip) throws Exception {
        int pad = WakeWordDetector.SAMPLE_RATE;
        short[] audio = new short[pad + clip.length + pad];
        System.arraycopy(clip, 0, audio, pad, clip.length);
        d.reset();
        float max = 0f;
        short[] chunk = new short[WakeWordDetector.CHUNK];
        for (int i = 0; i + WakeWordDetector.CHUNK < audio.length; i += WakeWordDetector.CHUNK) {
            System.arraycopy(audio, i, chunk, 0, WakeWordDetector.CHUNK);
            max = Math.max(max, d.process(chunk));
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
