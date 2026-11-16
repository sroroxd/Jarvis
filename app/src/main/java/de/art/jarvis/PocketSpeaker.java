package de.art.jarvis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import com.k2fsa.sherpa.onnx.GeneratedAudio;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Jarvis' Offline-Stimme (Pocket TTS, siehe {@link PocketVoice}) als Sprecher mit Warteschlange.
 *
 * <p>Zwei Threads arbeiten in einer Reihe: Der eine erzeugt den nächsten Satz, während der andere den vorigen
 * abspielt. Auf dem S24 Ultra rechnet Pocket rund dreimal schneller als Echtzeit (Messung vom 8. Nov.), deshalb ist
 * Satz 2 fertig, bevor Satz 1 ausgesprochen ist – ab dem zweiten Satz entsteht keine Pause.
 *
 * <p>Abbrechen ({@link #stop}) und „erster Satz eines neuen Vorgangs“ ({@code flush}) setzen eine Epoche hoch;
 * alles, was zu einer älteren Epoche gehört, wird verworfen und meldet nichts mehr. Rückmeldungen kommen auf dem
 * Haupt-Thread.
 */
final class PocketSpeaker {
    interface Listener {
        /** Erster Ton eines Satzes. */
        void onStart(int gen);

        /** Satz ganz gesprochen. */
        void onDone(int gen);

        /** Erzeugen oder Abspielen ging schief – der Satz {@code text} wurde nicht gesprochen. */
        void onError(int gen, String text, String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService synth = Executors.newSingleThreadExecutor();
    private final ExecutorService player = Executors.newSingleThreadExecutor();
    private final AtomicInteger epoch = new AtomicInteger();
    /** Abspielaufträge, die schon in der Warteschlange stehen – solange > 0, bleibt der AudioTrack offen. */
    private final AtomicInteger queuedPlays = new AtomicInteger();
    private volatile boolean voiceCall;
    private volatile boolean released;
    private PocketVoice voice; // nur auf dem Erzeuger-Thread
    private boolean loadFailed;
    // Abspiel-Thread; cancel() greift vom Haupt-Thread aus nur über pause()/flush() zu:
    private volatile AudioTrack track;
    /** Nach einem Abbruch ist der Track pausiert und geleert – der nächste Satz muss einen frischen bekommen. */
    private volatile boolean trackStale;
    private long framesWritten;

    /**
     * Messwerte des letzten Satzes für den Startbildschirm („Offline-Stimme: … Aussetzer 0“): Rechenzeit gegen
     * Sprechdauer und wie oft der Ton ausgesetzt hat. So lässt sich Knacken und Stocken ohne USB-Kabel zuordnen.
     */
    static volatile String lastStats = "";

    PocketSpeaker(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** Im Telefonmodus als Sprachkommunikation abspielen, damit die Echo-Unterdrückung greift (siehe TalkAudio). */
    void setVoiceCall(boolean on) {
        voiceCall = on;
    }

    /** Modell und Stimme schon vor dem ersten Satz laden und einmal warmlaufen lassen (kostet einmal ~2 s). */
    void prepare() {
        synth.execute(() -> {
            if (ensureVoice() != null) {
                try {
                    voice.generate("Zu Diensten."); // liest die Sprachprobe ein
                } catch (Throwable ignored) {
                    // Warmlaufen ist nur eine Hilfe
                }
            }
        });
    }

    /** Kann Pocket gerade sprechen (Dateien da, Laden nicht schon gescheitert)? */
    boolean usable() {
        return !released && !loadFailed && PocketTrial.downloaded(context);
    }

    /**
     * Stellt einen Satz an. {@code flush}: erster Satz eines neuen Sprechvorgangs – bricht Älteres ab.
     */
    void speak(String text, int gen, boolean flush) {
        if (flush) cancel();
        final int e = epoch.get();
        synth.execute(() -> {
            if (e != epoch.get() || released) return;
            // Etwas niedriger als Standard (nicht „Hintergrund“: das schränkt die Rechenzeit stark ein). Die
            // Rechen-Threads der ONNX Runtime erben das beim Anlegen.
            Process.setThreadPriority(5);
            final long t0 = SystemClock.elapsedRealtime();
            PocketVoice v = ensureVoice();
            if (v == null) {
                post(e, () -> listener.onError(gen, text, "Offline-Stimme lässt sich nicht laden."));
                return;
            }
            GeneratedAudio audio;
            try {
                audio = v.generate(text);
            } catch (Throwable t) {
                post(e, () -> listener.onError(gen, text, "Offline-Stimme: " + t.getMessage()));
                return;
            }
            if (e != epoch.get() || released) return;
            final long synthMs = SystemClock.elapsedRealtime() - t0;
            queuedPlays.incrementAndGet();
            player.execute(() -> {
                try {
                    play(audio.getSamples(), audio.getSampleRate(), e, gen, text, synthMs);
                } finally {
                    // Kanal offen lassen und nur anhalten – Schließen und neu Öffnen knackt zwischen den Sätzen.
                    if (queuedPlays.decrementAndGet() == 0) idleTrack();
                }
            });
        });
    }

    /** Bricht alles ab, was gerade gesprochen wird oder ansteht. */
    void stop() {
        cancel();
    }

    void release() {
        released = true;
        cancel();
        synth.execute(() -> {
            if (voice != null) {
                voice.release();
                voice = null;
            }
        });
        synth.shutdown();
        player.execute(this::closeTrack);
        player.shutdown();
    }

    private void cancel() {
        epoch.incrementAndGet();
        AudioTrack t = track;
        if (t != null) {
            trackStale = true;
            try {
                t.pause();
                t.flush();
            } catch (RuntimeException ignored) {
                // schon freigegeben
            }
        }
    }

    private PocketVoice ensureVoice() {
        if (voice != null) return voice;
        if (loadFailed) return null;
        try {
            File dir = PocketTrial.dir(context);
            // Drei Threads statt vier (Messung auf dem S24 Ultra: vier = 0,3-fache Echtzeit, Luft genug): Mikrofon,
            // Spracherkennung und Wiedergabe brauchen daneben ebenfalls Rechenzeit.
            int threads = Math.max(2, Math.min(3, Runtime.getRuntime().availableProcessors() / 2));
            voice = new PocketVoice(dir, new File(dir, "stimme.wav"), threads);
        } catch (Throwable t) { // auch UnsatisfiedLinkError
            loadFailed = true;
            return null;
        }
        return voice;
    }

    private void post(int e, Runnable r) {
        main.post(() -> {
            if (e == epoch.get() && !released) r.run();
        });
    }

    /** Spielt einen Satz; kehrt zurück, wenn er ausgesprochen ist oder abgebrochen wurde. */
    private void play(float[] floats, int rate, int e, int gen, String text, long synthMs) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        final short[] samples = PcmConvert.toPcm16(floats, rate);
        if (e != epoch.get() || samples.length == 0) {
            if (e == epoch.get()) post(e, () -> listener.onDone(gen));
            return;
        }
        try {
            if (trackStale) {
                closeTrack();
                trackStale = false;
            }
            if (track == null) {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(TalkAudio.speech(voiceCall))
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(Math.max(AudioTrack.getMinBufferSize(rate,
                                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), rate / 2 * 2))
                        .build();
                framesWritten = 0;
            }
            if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) track.play();
            post(e, () -> listener.onStart(gen));
            int off = 0;
            while (off < samples.length) {
                if (e != epoch.get()) return;
                int n = track.write(samples, off, Math.min(samples.length - off, rate / 20),
                        AudioTrack.WRITE_NON_BLOCKING);
                if (n < 0) throw new IllegalStateException("AudioTrack.write " + n);
                if (n == 0) SystemClock.sleep(5);
                off += n;
                framesWritten += n;
            }
            // Warten, bis alles gespielt ist (höchstens etwas länger als der Satz selbst dauert).
            long deadline = SystemClock.elapsedRealtime() + samples.length * 1000L / rate + 1500;
            while (track.getPlaybackHeadPosition() < framesWritten && e == epoch.get()
                    && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(10);
            }
            final String stats = String.format(java.util.Locale.GERMANY,
                    "Offline-Stimme: %.1f s Rechenzeit für %.1f s Sprache · Aussetzer %d",
                    synthMs / 1000f, samples.length / (float) rate, track.getUnderrunCount());
            lastStats = stats;
            post(e, () -> listener.onDone(gen));
        } catch (RuntimeException ex) {
            closeTrack();
            post(e, () -> listener.onError(gen, text, "Wiedergabe: " + ex.getMessage()));
        }
    }

    /** Nach dem letzten Satz: Kanal anhalten (nicht schließen), damit nichts im Leerlauf läuft und nichts knackt. */
    private void idleTrack() {
        AudioTrack t = track;
        if (t != null && !trackStale) {
            try {
                if (t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) t.pause();
            } catch (RuntimeException ignored) {
                // schon freigegeben
            }
        }
    }

    private void closeTrack() {
        AudioTrack t = track;
        track = null;
        if (t != null) {
            try {
                t.stop();
            } catch (RuntimeException ignored) {
                // nicht gestartet
            }
            t.release();
        }
    }
}
