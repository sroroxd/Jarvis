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

/**
 * Dazwischenreden im Dauergespräch: Während Jarvis spricht, lauscht dieses Mikrofon, ob Art
 * anfängt zu reden – dann bricht Jarvis sofort ab und hört zu.
 *
 * Aufnahme über {@code VOICE_COMMUNICATION}: Nur diese Quelle läuft durch die Echo- und
 * Rauschunterdrückung des Handys. Mit der Spracherkennungs-Quelle hörte Jarvis seine eigene
 * Stimme und unterbräche sich ständig selbst. Was an Echo trotzdem durchkommt, fängt
 * {@link SpeechGate} ab.
 *
 * Alle öffentlichen Methoden laufen auf dem Haupt-Thread.
 */
final class BargeIn {
    interface Listener {
        void onArtSpeaks();
    }

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> afterStop = new ArrayList<>();
    private Thread thread;
    private volatile boolean active;
    private volatile boolean echoCanceller;

    BargeIn(Listener listener) {
        this.listener = listener;
    }

    boolean isRunning() {
        return thread != null;
    }

    /** Ob die Echo-Unterdrückung des Handys zuletzt angelaufen ist (für die Anzeige). */
    boolean echoCancellerActive() {
        return echoCanceller;
    }

    void start() {
        if (thread != null) return;
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

    @SuppressLint("MissingPermission") // der Dienst prüft die Mikrofon-Berechtigung vorher
    private void loop() {
        AudioRecord record = null;
        AcousticEchoCanceler aec = null;
        NoiseSuppressor ns = null;
        boolean heard = false;
        try {
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
            SpeechGate gate = new SpeechGate();
            short[] frame = new short[SpeechGate.FRAME];
            while (active) {
                int filled = 0;
                while (filled < frame.length && active) {
                    int n = record.read(frame, filled, frame.length - filled);
                    if (n < 0) return; // Mikrofon weg – dann eben kein Dazwischenreden
                    filled += n;
                }
                if (!active) break;
                if (gate.feed(frame, 0, frame.length)) {
                    heard = true;
                    active = false; // Mikrofon sofort für die Spracherkennung freigeben
                }
            }
        } catch (Throwable ignored) {
            // Kein Dazwischenreden ist besser als ein Absturz: Jarvis spricht dann einfach zu Ende.
        } finally {
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

    private void finished(boolean speech) {
        thread = null;
        active = false;
        List<Runnable> callbacks = new ArrayList<>(afterStop);
        afterStop.clear();
        for (Runnable r : callbacks) r.run();
        if (speech) listener.onArtSpeaks();
    }
}
