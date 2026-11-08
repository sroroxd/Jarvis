package de.art.jarvis;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import de.art.jarvis.wake.WakeWordDetector;

/**
 * Dazwischenreden im Dauergespräch: Während Jarvis spricht, lauscht dieses Mikrofon, ob Art
 * anfängt zu reden – dann bricht Jarvis sofort ab und hört zu.
 *
 * Aufnahme über {@code VOICE_COMMUNICATION}: Nur diese Quelle läuft durch die Echo- und
 * Rauschunterdrückung des Handys. Mit der Spracherkennungs-Quelle hörte Jarvis seine eigene
 * Stimme und unterbräche sich ständig selbst. Was an Echo trotzdem durchkommt, fängt
 * {@link SpeechGate} ab.
 *
 * Zusätzlich läuft auf demselben, echobereinigten Ton das Wachwort: „Jarvis“ unterbricht immer,
 * auch wenn Art leiser spricht, als die Lautstärke-Schranke verlangt. Vorher war das Wachwort-Mikrofon
 * während des Sprechens im Dauergespräch ganz aus – obwohl der Startbildschirm „„Jarvis“ unterbricht“ anzeigt.
 *
 * Alle öffentlichen Methoden laufen auf dem Haupt-Thread.
 */
final class BargeIn {
    interface Listener {
        void onArtSpeaks();
    }

    /** Lädt den Wachwort-Erkenner (läuft auf dem Aufnahme-Thread, darf werfen). */
    interface DetectorLoader {
        WakeWordDetector load() throws Exception;
    }

    private final Listener listener;
    private final DetectorLoader loader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> afterStop = new ArrayList<>();
    private Thread thread;
    private volatile boolean active;
    private volatile boolean echoCanceller;
    /** Abstand zum Echo in dB (Regler „Empfindlichkeit beim Dazwischenreden“). */
    private volatile double margin = SpeechGate.MARGIN_DB;
    /** Schwelle des Wachworts; negativ = Wachwort aus. */
    private volatile float wakeThreshold = -1f;
    /** Ist Jarvis' Stimme in dieser Antwort schon angelaufen? Siehe {@link SpeechGate#voiceStarted()}. */
    private volatile boolean voiceStarted;
    /** Schranke des laufenden Durchgangs (nur zum Weiterreichen des Startsignals). */
    private volatile SpeechGate gate;
    /**
     * Eigener Erkenner, nur vom Aufnahme-Thread benutzt. Ein neuer Durchgang startet erst, wenn der
     * vorige beendet ist ({@link #finished}), deshalb nie gleichzeitig. Bleibt zwischen den Antworten
     * geladen und wird erst mit {@link #release()} geschlossen.
     */
    private WakeWordDetector detector;
    private boolean detectorFailed;
    private boolean releaseRequested;

    BargeIn(Listener listener, DetectorLoader loader) {
        this.listener = listener;
        this.loader = loader;
    }

    boolean isRunning() {
        return thread != null;
    }

    /** Ob die Echo-Unterdrückung des Handys zuletzt angelaufen ist (für die Anzeige). */
    boolean echoCancellerActive() {
        return echoCanceller;
    }

    /** Neue Antwort beginnt: Die Schranke lernt erst, wenn deren Stimme wirklich zu hören ist. */
    void newUtterance() {
        voiceStarted = false;
    }

    /** Jarvis' Stimme ist angelaufen (Sprachausgabe bzw. ElevenLabs meldet den Start). */
    void voiceStarted() {
        voiceStarted = true;
        SpeechGate g = gate;
        if (g != null) g.voiceStarted();
    }

    void setMargin(double marginDb) {
        margin = marginDb;
    }

    /** Wachwort-Schwelle wie beim normalen Wachwort; negativ schaltet es hier aus. */
    void setWakeThreshold(float threshold) {
        wakeThreshold = threshold;
    }

    void start() {
        if (thread != null || releaseRequested) return;
        active = true;
        Thread t = new Thread(this::loop, "jarvis-dazwischenreden");
        thread = t;
        t.start();
    }

    /** Hält an und gibt das Mikrofon frei; {@code then} läuft, sobald es wirklich frei ist. */
    void stop(Runnable then) {
        if (thread == null) {
            if (then != null) then.run();
            return;
        }
        active = false;
        if (then != null) afterStop.add(then);
    }

    /** Beim Beenden von Jarvis: anhalten und den Wachwort-Erkenner freigeben. */
    void release() {
        releaseRequested = true;
        stop(null);
        if (thread == null) closeDetector();
    }

    @SuppressLint("MissingPermission") // der Dienst prüft die Mikrofon-Berechtigung vorher
    private void loop() {
        AudioRecord record = null;
        AcousticEchoCanceler aec = null;
        NoiseSuppressor ns = null;
        boolean heard = false;
        try {
            WakeWordDetector wakeWord = wakeThreshold >= 0 ? detector() : null;
            if (wakeWord != null) wakeWord.reset();
            int min = AudioRecord.getMinBufferSize(SpeechGate.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min, SpeechGate.FRAME * 2 * 8);
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SpeechGate.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) return;
            int session = record.getAudioSessionId();
            echoCanceller = false;
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(session);
                if (aec != null) echoCanceller = aec.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS;
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(session);
                if (ns != null) ns.setEnabled(true);
            }
            record.startRecording();
            SpeechGate g = new SpeechGate(margin);
            gate = g;
            if (voiceStarted) g.voiceStarted(); // Stimme lief schon, bevor das Mikrofon frei war
            short[] frame = new short[SpeechGate.FRAME];
            // Das Wachwort braucht 80-ms-Blöcke: genau vier 20-ms-Blöcke der Schranke.
            short[] chunk = new short[WakeWordDetector.CHUNK];
            int chunkFill = 0;
            while (active) {
                int filled = 0;
                while (filled < frame.length && active) {
                    int n = record.read(frame, filled, frame.length - filled);
                    if (n < 0) return; // Mikrofon weg – dann eben kein Dazwischenreden
                    filled += n;
                }
                if (!active) break;
                if (g.feed(frame, 0, frame.length)) heard = true;
                if (!heard && wakeWord != null) {
                    System.arraycopy(frame, 0, chunk, chunkFill, frame.length);
                    chunkFill += frame.length;
                    if (chunkFill == chunk.length) {
                        chunkFill = 0;
                        float threshold = wakeThreshold;
                        try {
                            if (threshold >= 0 && wakeWord.process(chunk) >= threshold) heard = true;
                        } catch (Exception e) {
                            wakeWord = null; // nur das Wachwort fällt aus, die Lautstärke-Schranke läuft weiter
                        }
                    }
                }
                if (heard) active = false; // Mikrofon sofort für die Spracherkennung freigeben
            }
        } catch (Throwable ignored) {
            // Kein Dazwischenreden ist besser als ein Absturz: Jarvis spricht dann einfach zu Ende.
        } finally {
            gate = null;
            if (aec != null) aec.release();
            if (ns != null) ns.release();
            if (record != null) {
                try {
                    record.stop();
                } catch (Exception ignored) {
                    // war nicht gestartet
                }
                record.release();
            }
            final boolean speech = heard;
            main.post(() -> finished(speech));
        }
    }

    /** Erkenner laden (einmal) – schlägt das fehl, bleibt es bei der Lautstärke-Schranke. Nur Aufnahme-Thread. */
    private WakeWordDetector detector() {
        if (detector == null && !detectorFailed && loader != null) {
            try {
                detector = loader.load();
            } catch (Throwable e) {
                detectorFailed = true;
            }
        }
        return detector;
    }

    private void finished(boolean speech) {
        thread = null;
        active = false;
        List<Runnable> callbacks = new ArrayList<>(afterStop);
        afterStop.clear();
        for (Runnable r : callbacks) r.run();
        if (releaseRequested) {
            closeDetector();
            return;
        }
        if (speech) listener.onArtSpeaks();
    }

    private void closeDetector() {
        if (detector != null) {
            detector.close();
            detector = null;
        }
    }
}
