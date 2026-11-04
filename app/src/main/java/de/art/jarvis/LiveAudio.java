package de.art.jarvis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tonleitung für den Gesprächsmodus: Mikrofon hinein, Antwort hinaus,
 * beides gleichzeitig und mit Echo-Unterdrückung.
 *
 * Die drei Einstellungen, auf die es ankommt – jede einzeln nachgelesen,
 * weil eine falsche davon den ganzen Modus unbrauchbar macht:
 *
 * 1. Aufnahme über {@code VOICE_COMMUNICATION}: nur diese Quelle schickt das
 *    Signal durch die Echo- und Rauschunterdrückung der Hardware. Ohne sie
 *    hört Jarvis seine eigene Stimme und antwortet sich selbst im Kreis.
 * 2. Ausgabe über {@code USAGE_MEDIA}, nicht {@code USAGE_VOICE_COMMUNICATION}:
 *    letzteres landet auf dem Telefoniekanal, dessen Lautstärke eine normale
 *    App nicht setzen kann und der im Emulator stumm bleibt. Für die
 *    Echo-Kopplung zählt die Aufnahmeseite, nicht die Ausgabeseite.
 * 3. {@code MODE_IN_COMMUNICATION} für die Dauer des Gesprächs: mehrere Geräte
 *    schalten die Echo-Unterdrückung erst in diesem Modus ein.
 *
 * Die Hardware-Echo-Unterdrückung ist geräteabhängig und bei Samsung
 * historisch unzuverlässig. {@link #aecActive()} sagt, ob sie auf diesem Gerät
 * überhaupt angelaufen ist – damit die App ehrlich berichten kann statt zu raten.
 */
final class LiveAudio {
    private static final String TAG = "JarvisLiveAudio";

    static final int IN_RATE = 16000;
    static final int OUT_RATE = LiveProtocol.OUTPUT_SAMPLE_RATE;

    /** 100 ms bei 16 kHz, 16 Bit, mono. Kleiner = mehr Netzlast, kaum schneller. */
    private static final int CHUNK_BYTES = 3200;

    interface Mic {
        /** Ein frischer Mikrofonblock. Läuft auf dem Mikrofon-Thread. */
        void onChunk(byte[] pcm);
    }

    interface Problem {
        void onProblem(String german);
    }

    private final Context context;
    private final Mic mic;
    private final Problem problem;

    private AudioRecord record;
    private AudioTrack track;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor noise;
    private AudioManager audioManager;
    private int previousMode = AudioManager.MODE_NORMAL;

    private Thread micThread;
    private Thread playThread;
    private volatile boolean running;

    private final BlockingQueue<byte[]> playQueue = new ArrayBlockingQueue<>(64);
    private final Object trackLock = new Object();

    private volatile boolean aecActive;
    private volatile boolean noiseActive;
    /** Zählt Unterbrechungen: Blöcke aus einer alten Antwort werden verworfen. */
    private volatile int generation;

    LiveAudio(Context context, Mic mic, Problem problem) {
        this.context = context;
        this.mic = mic;
        this.problem = problem;
    }

    boolean aecActive() { return aecActive; }

    boolean noiseActive() { return noiseActive; }

    /** @return false, wenn das Mikrofon nicht geöffnet werden konnte. */
    boolean start() {
        if (running) return true;
        try {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                previousMode = audioManager.getMode();
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            }

            int minIn = AudioRecord.getMinBufferSize(IN_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minIn <= 0) minIn = CHUNK_BYTES * 4;
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minIn, CHUNK_BYTES * 4));
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                fail("Das Mikrofon lässt sich nicht öffnen. Prüf die Mikrofon-Berechtigung.");
                stop();
                return false;
            }

            attachEffects(record.getAudioSessionId());

            int minOut = AudioTrack.getMinBufferSize(OUT_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minOut <= 0) minOut = 8192;
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(OUT_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(minOut * 4)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            running = true;
            record.startRecording();
            track.play();

            micThread = new Thread(this::pumpMic, "jarvis-mic");
            micThread.start();
            playThread = new Thread(this::pumpPlayback, "jarvis-play");
            playThread.start();
            return true;
        } catch (SecurityException e) {
            fail("Keine Mikrofon-Berechtigung für den Gesprächsmodus.");
            stop();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Tonleitung ging nicht auf", e);
            fail("Die Tonleitung ging nicht auf: " + e.getClass().getSimpleName());
            stop();
            return false;
        }
    }

    private void attachEffects(int session) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(session);
                if (aec != null) {
                    aec.setEnabled(true);
                    aecActive = aec.getEnabled();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Echo-Unterdrückung nicht verfügbar", e);
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noise = NoiseSuppressor.create(session);
                if (noise != null) {
                    noise.setEnabled(true);
                    noiseActive = noise.getEnabled();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Rauschunterdrückung nicht verfügbar", e);
        }
        Log.i(TAG, "Echo-Unterdrückung: " + aecActive + ", Rauschunterdrückung: " + noiseActive);
    }

    private void pumpMic() {
        byte[] buffer = new byte[CHUNK_BYTES];
        while (running) {
            AudioRecord r = record;
            if (r == null) break;
            int read;
            try {
                read = r.read(buffer, 0, buffer.length);
            } catch (Exception e) {
                break;
            }
            if (read <= 0) {
                if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_DEAD_OBJECT) break;
                continue;
            }
            byte[] chunk = new byte[read];
            System.arraycopy(buffer, 0, chunk, 0, read);
            try {
                mic.onChunk(chunk);
            } catch (Exception e) {
                Log.w(TAG, "Mikrofonblock konnte nicht verschickt werden", e);
            }
        }
    }

    private void pumpPlayback() {
        while (running) {
            byte[] chunk;
            try {
                chunk = playQueue.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return;
            }
            if (chunk == null) continue;

            // Bewusst nicht blockierend schreiben: ein blockierendes write() würde
            // das Schloss für bis zu eine halbe Sekunde halten und damit das
            // Dazwischenreden verzögern – genau das, was dieser Modus können soll.
            final int mine = generation;
            int offset = 0;
            while (running && offset < chunk.length) {
                if (mine != generation) break; // Art redet, dieser Block ist veraltet
                int written;
                synchronized (trackLock) {
                    AudioTrack t = track;
                    if (t == null) break;
                    try {
                        written = t.write(chunk, offset, chunk.length - offset,
                                AudioTrack.WRITE_NON_BLOCKING);
                    } catch (Exception e) {
                        Log.w(TAG, "Ausgabe fehlgeschlagen", e);
                        break;
                    }
                }
                if (written < 0) {
                    Log.w(TAG, "Ausgabe meldet Fehler " + written);
                    break;
                }
                offset += written;
                if (written == 0) {
                    // Puffer voll – kurz warten, statt den Kern zu belasten.
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }
    }

    /** Antwortblock in die Ausgabe einreihen. Volle Warteschlange: ältesten verwerfen. */
    void play(byte[] pcm24k) {
        if (!running || pcm24k == null || pcm24k.length == 0) return;
        if (!playQueue.offer(pcm24k)) {
            playQueue.poll();
            playQueue.offer(pcm24k);
        }
    }

    /**
     * Art hat dazwischengeredet: alles Gepufferte sofort verwerfen.
     * pause/flush/play ist der dokumentierte Weg, eine laufende Ausgabe zu leeren.
     * Die erhöhte Zählung bricht zusätzlich den gerade laufenden Schreibvorgang ab.
     */
    void dropPlayback() {
        generation++;
        playQueue.clear();
        synchronized (trackLock) {
            AudioTrack t = track;
            if (t == null) return;
            try {
                t.pause();
                t.flush();
                t.play();
            } catch (Exception e) {
                Log.w(TAG, "Ausgabe konnte nicht geleert werden", e);
            }
        }
    }

    /** True, solange noch Antwortton aussteht. */
    boolean speaking() {
        return !playQueue.isEmpty();
    }

    void stop() {
        running = false;
        playQueue.clear();

        Thread m = micThread;
        micThread = null;
        if (m != null) m.interrupt();
        Thread p = playThread;
        playThread = null;
        if (p != null) p.interrupt();

        AudioRecord r = record;
        record = null;
        if (r != null) {
            try {
                if (r.getState() == AudioRecord.STATE_INITIALIZED) r.stop();
            } catch (Exception ignored) {
            }
            r.release();
        }

        synchronized (trackLock) {
            AudioTrack t = track;
            track = null;
            if (t != null) {
                try {
                    t.pause();
                    t.flush();
                    t.stop();
                } catch (Exception ignored) {
                }
                t.release();
            }
        }

        if (aec != null) {
            try { aec.release(); } catch (Exception ignored) { }
            aec = null;
        }
        if (noise != null) {
            try { noise.release(); } catch (Exception ignored) { }
            noise = null;
        }
        aecActive = false;
        noiseActive = false;

        AudioManager am = audioManager;
        audioManager = null;
        if (am != null) {
            try { am.setMode(previousMode); } catch (Exception ignored) { }
        }
    }

    private void fail(String german) {
        if (problem != null) problem.onProblem(german);
    }
}
