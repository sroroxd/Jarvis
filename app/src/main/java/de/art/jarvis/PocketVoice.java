package de.art.jarvis;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Offline-Stimme mit Kyutais Pocket TTS über sherpa-onnx (Machbarkeit, 8. Nov.). Läuft ganz auf dem Handy, ohne
 * Netz und ohne Kosten pro Satz – gedacht als Ersatz für ElevenLabs.
 *
 * <p>Warum sherpa-onnx statt einer eigenen JNI-Hülle um PocketTTS.cpp: sherpa-onnx (Apache 2.0) bringt Pocket TTS seit
 * Version 2.0 mit fertiger Android-Bibliothek und Java-Schnittstelle mit; PocketTTS.cpp nennt nur Linux, macOS und
 * Windows und hätte eine eigene NDK-Werkzeugkette gebraucht. Beide nutzen dieselben ONNX-Modelle.
 *
 * <p>Modell: Kyutai Pocket TTS Deutsch (CC BY 4.0), als sherpa-onnx-Paket von haex-space/pocket-tts-sherpa-onnx,
 * Ordner {@code de} (rund 140 MB). Pocket TTS hat keine eingebauten Stimmen – die Stimme kommt aus einer kurzen
 * Sprachprobe (WAV, 16 bit oder float, mono), die sherpa-onnx zwischenspeichert. Nur der erste Satz mit einer neuen
 * Probe bezahlt das Einlesen.
 *
 * <p>Nicht auf dem Haupt-Thread benutzen: Laden dauert rund eine Sekunde, ein Satz mehrere hundert Millisekunden.
 */
final class PocketVoice {
    /** Dateien eines Modell-Ordners, wie sherpa-onnx sie für Pocket TTS erwartet. */
    static final String[] FILES = {"lm_flow.int8.onnx", "lm_main.int8.onnx", "encoder.onnx", "decoder.int8.onnx",
            "text_conditioner.onnx", "vocab.json", "token_scores.json"};

    private final OfflineTts tts;
    private final float[] reference;
    private final int referenceRate;

    /** Liegen alle Modelldateien im Ordner? */
    static boolean present(File dir) {
        for (String f : FILES) {
            if (!new File(dir, f).isFile()) return false;
        }
        return true;
    }

    PocketVoice(File modelDir, File voiceWav, int threads) throws IOException {
        Wav wav = Wav.read(voiceWav);
        reference = wav.samples;
        referenceRate = wav.rate;
        OfflineTtsPocketModelConfig pocket = new OfflineTtsPocketModelConfig();
        pocket.setLmFlow(path(modelDir, "lm_flow.int8.onnx"));
        pocket.setLmMain(path(modelDir, "lm_main.int8.onnx"));
        pocket.setEncoder(path(modelDir, "encoder.onnx"));
        pocket.setDecoder(path(modelDir, "decoder.int8.onnx"));
        pocket.setTextConditioner(path(modelDir, "text_conditioner.onnx"));
        pocket.setVocabJson(path(modelDir, "vocab.json"));
        pocket.setTokenScoresJson(path(modelDir, "token_scores.json"));
        pocket.setVoiceEmbeddingCacheCapacity(4);
        OfflineTtsModelConfig model = new OfflineTtsModelConfig();
        model.setPocket(pocket);
        model.setNumThreads(threads);
        model.setProvider("cpu");
        OfflineTtsConfig config = new OfflineTtsConfig();
        config.setModel(model);
        config.setMaxNumSentences(1); // Satz für Satz – der erste ist hörbar, bevor der zweite fertig ist
        tts = new OfflineTts(null, config); // null: Dateien aus dem Dateisystem, nicht aus den Assets
    }

    int sampleRate() {
        return tts.sampleRate();
    }

    /** Spricht den Text (Satz für Satz); gibt das ganze Audio zurück. */
    GeneratedAudio generate(String text) {
        GenerationConfig gc = new GenerationConfig();
        gc.setReferenceAudio(reference);
        gc.setReferenceSampleRate(referenceRate);
        gc.setNumSteps(1);
        Map<String, String> extra = new HashMap<>();
        extra.put("max_reference_audio_len", "12");
        gc.setExtra(extra);
        return tts.generateWithConfig(text, gc);
    }

    void release() {
        tts.release();
    }

    private static String path(File dir, String name) {
        return new File(dir, name).getAbsolutePath();
    }
}
