package de.art.jarvis;

import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import de.art.jarvis.wake.WakeWordDetector;

/**
 * Lädt das Offline-Modell für „Jarvis“ (openWakeWord-Format, eigenes Training unter training/wakeword).
 *
 * <p>Seit „Immer zuhören“ (Wunsch #47, 8. Nov.) gibt es kein Wachwort zum Aktivieren mehr; das frühere
 * WakeWordEngine mit eigenem Mikrofon ist entfernt. Das Modell braucht nur noch {@link BargeIn}: Sagt Art „Jarvis“,
 * während Jarvis spricht, hört er sofort auf.
 */
final class WakeModel {
    private static final String DIR = "wakeword/";

    private WakeModel() {}

    /** Die drei Modelle: Mel-Spektrogramm, Einbettung und das eigene „Jarvis“-Modell. */
    static WakeWordDetector load(AssetManager assets) throws Exception {
        return new WakeWordDetector(
                read(assets, DIR + "melspectrogram.onnx"),
                read(assets, DIR + "embedding_model.onnx"),
                // Eigenes Modell (training/wakeword): erkennt „Jarvis“ allein, englisch und deutsch ausgesprochen,
                // und „Hey/Okay Jarvis“. Das alte hey_jarvis-Modell zusätzlich brachte in Messungen keinen Treffer mehr,
                // nur mehr Fehlalarme – deshalb nur dieses eine.
                read(assets, DIR + "jarvis.onnx"));
    }

    private static byte[] read(AssetManager assets, String name) throws IOException {
        try (InputStream in = assets.open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
