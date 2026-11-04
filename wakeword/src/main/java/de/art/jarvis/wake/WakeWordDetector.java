package de.art.jarvis.wake;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * Wachwort-Erkennung nach openWakeWord (https://github.com/dscripka/openWakeWord), komplett offline.
 *
 * <p>Ablauf je 80-ms-Block (1280 Samples, 16 kHz, 16 bit mono):
 * Audio → Mel-Spektrogramm → Sprach-Merkmale (Googles „speech_embedding“) → Wahrscheinlichkeit für das Wachwort.
 * Die Logik entspricht Schritt für Schritt der Python-Vorlage (geprüft mit Testaufnahmen, siehe Tests).
 *
 * <p>Es lassen sich mehrere Wachwort-Modelle gleichzeitig auswerten (z. B. „Jarvis“ und „Hey Jarvis“).
 * Mel-Spektrogramm und Merkmale – der teure Teil – werden dabei nur einmal berechnet; geliefert wird der höchste Wert.
 *
 * <p>Nicht threadsicher: nur von einem Thread aus benutzen.
 */
public final class WakeWordDetector implements AutoCloseable {
    public static final int SAMPLE_RATE = 16000;
    /** Ein Block = 80 ms. */
    public static final int CHUNK = 1280;

    /** Wie im Original: drei Mel-Frames (3 × 160 Samples) Überlappung zum vorherigen Block. */
    private static final int CONTEXT = CHUNK + 480;
    private static final int MEL_BINS = 32;
    private static final int MEL_WINDOW = 76;
    private static final int EMBEDDING = 96;
    /** Die ersten Vorhersagen nach dem Start sind unzuverlässig und werden wie im Original verworfen. */
    private static final int WARMUP = 5;

    private final OrtEnvironment env;
    private final OrtSession.SessionOptions options;
    private final OrtSession mel;
    private final OrtSession embedding;
    private final OrtSession[] wakeWords;
    private final String melInput;
    private final String embeddingInput;
    private final String[] wakeWordInputs;
    private final int[] headFrames;
    private final int featureFrames;

    private final float[] window = new float[CONTEXT];
    private int filled;
    private final ArrayDeque<float[]> melFrames = new ArrayDeque<>();
    private final ArrayDeque<float[]> features = new ArrayDeque<>();
    private int predictions;

    public WakeWordDetector(byte[] melModel, byte[] embeddingModel, byte[]... wakeWordModels) throws OrtException {
        if (wakeWordModels == null || wakeWordModels.length == 0) {
            throw new IllegalArgumentException("Mindestens ein Wachwort-Modell nötig");
        }
        env = OrtEnvironment.getEnvironment();
        options = new OrtSession.SessionOptions();
        // Ein Rechenkern reicht völlig und schont den Akku.
        options.setIntraOpNumThreads(1);
        options.setInterOpNumThreads(1);
        mel = env.createSession(melModel, options);
        embedding = env.createSession(embeddingModel, options);
        wakeWords = new OrtSession[wakeWordModels.length];
        wakeWordInputs = new String[wakeWordModels.length];
        headFrames = new int[wakeWordModels.length];
        int frames = 0;
        for (int i = 0; i < wakeWordModels.length; i++) {
            wakeWords[i] = env.createSession(wakeWordModels[i], options);
            wakeWordInputs[i] = wakeWords[i].getInputNames().iterator().next();
            headFrames[i] = inputFrames(wakeWords[i], wakeWordInputs[i]);
            frames = Math.max(frames, headFrames[i]);
        }
        melInput = mel.getInputNames().iterator().next();
        embeddingInput = embedding.getInputNames().iterator().next();
        featureFrames = frames;
        reset();
    }

    /** Anzahl der Merkmal-Frames, die ein Wachwort-Modell erwartet (bei „Jarvis“ und „Hey Jarvis“: 16). */
    private static int inputFrames(OrtSession session, String input) throws OrtException {
        NodeInfo info = session.getInputInfo().get(input);
        if (info != null && info.getInfo() instanceof TensorInfo) {
            long[] shape = ((TensorInfo) info.getInfo()).getShape();
            if (shape.length >= 2 && shape[1] > 0) return (int) shape[1];
        }
        return 16;
    }

    /** Vergisst alles Gehörte – nach jedem Neustart des Mikrofons aufrufen. */
    public void reset() {
        filled = 0;
        Arrays.fill(window, 0f);
        melFrames.clear();
        for (int i = 0; i < MEL_WINDOW; i++) {
            float[] ones = new float[MEL_BINS];
            Arrays.fill(ones, 1f);
            melFrames.addLast(ones);
        }
        features.clear();
        predictions = 0;
    }

    /**
     * Verarbeitet genau einen Block und liefert die Wahrscheinlichkeit (0 … 1), dass gerade das Wachwort fiel.
     */
    public float process(short[] pcm) throws OrtException {
        if (pcm == null || pcm.length != CHUNK) {
            throw new IllegalArgumentException("Erwartet genau " + CHUNK + " Samples");
        }
        // 1. Die letzten CONTEXT Samples (neuer Block plus Überlappung) bereitstellen.
        int keep = Math.min(filled, CONTEXT - CHUNK);
        System.arraycopy(window, filled - keep, window, 0, keep);
        for (int i = 0; i < CHUNK; i++) window[keep + i] = pcm[i];
        filled = keep + CHUNK;
        float[] audio = filled == CONTEXT ? window : Arrays.copyOf(window, filled);

        // 2. Mel-Spektrogramm, mit derselben Umrechnung wie im Original (x / 10 + 2).
        float[] spec = run(mel, melInput, audio, new long[]{1, filled});
        int frames = spec.length / MEL_BINS;
        for (int f = 0; f < frames; f++) {
            float[] row = new float[MEL_BINS];
            for (int b = 0; b < MEL_BINS; b++) row[b] = spec[f * MEL_BINS + b] / 10f + 2f;
            melFrames.addLast(row);
        }
        while (melFrames.size() > MEL_WINDOW) melFrames.removeFirst();

        // 3. Sprach-Merkmale aus den letzten 76 Mel-Frames.
        float[] melInputData = new float[MEL_WINDOW * MEL_BINS];
        int k = 0;
        for (float[] row : melFrames) {
            System.arraycopy(row, 0, melInputData, k, MEL_BINS);
            k += MEL_BINS;
        }
        float[] vector = run(embedding, embeddingInput, melInputData, new long[]{1, MEL_WINDOW, MEL_BINS, 1});
        features.addLast(Arrays.copyOf(vector, EMBEDDING));
        while (features.size() > featureFrames) features.removeFirst();
        if (features.size() < featureFrames) return 0f;

        // 4. Jedes Wachwort-Modell über seine letzten Merkmal-Frames; der höchste Wert zählt.
        predictions++;
        float best = 0f;
        float[][] all = features.toArray(new float[0][]);
        for (int h = 0; h < wakeWords.length; h++) {
            int n = headFrames[h];
            float[] featureData = new float[n * EMBEDDING];
            for (int f = 0; f < n; f++) {
                System.arraycopy(all[all.length - n + f], 0, featureData, f * EMBEDDING, EMBEDDING);
            }
            float[] out = run(wakeWords[h], wakeWordInputs[h], featureData, new long[]{1, n, EMBEDDING});
            if (out.length > 0) best = Math.max(best, out[0]);
        }
        if (predictions <= WARMUP) return 0f;
        return best;
    }

    private float[] run(OrtSession session, String input, float[] data, long[] shape) throws OrtException {
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape);
             OrtSession.Result result = session.run(Collections.singletonMap(input, tensor))) {
            FloatBuffer out = ((OnnxTensor) result.get(0)).getFloatBuffer();
            float[] values = new float[out.remaining()];
            out.get(values);
            return values;
        }
    }

    @Override
    public void close() {
        closeQuietly(mel);
        closeQuietly(embedding);
        for (OrtSession w : wakeWords) closeQuietly(w);
        options.close();
    }

    private static void closeQuietly(OrtSession s) {
        try {
            s.close();
        } catch (OrtException ignored) {
            // bereits geschlossen
        }
    }
}
