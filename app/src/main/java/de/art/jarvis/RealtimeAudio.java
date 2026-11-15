package de.art.jarvis;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Process;
import android.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Ein einziges Voll-Duplex-Mikrofon, PCM16/24kHz; begrenzte Puffer und sofortiges lokales Stoppen. */
final class RealtimeAudio implements AutoCloseable {
    private final Object lock = new Object();
    private static final class Chunk {
        final byte[] data;
        final int epoch;
        Chunk(byte[] data, int epoch) { this.data = data; this.epoch = epoch; }
    }
    private final ArrayBlockingQueue<Chunk> queue = new ArrayBlockingQueue<>(128);
    private volatile boolean closed;
    private AudioRecord record;
    private AudioTrack track;
    private AcousticEchoCanceler echo;
    private int epoch;
    private long writtenSamples;
    private long headBase;
    private Runnable drained;

    void start(Consumer<String> input, Consumer<String> error) {
        try {
            int inputSize = Math.max(4800, AudioRecord.getMinBufferSize(RealtimeProtocol.RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT));
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RealtimeProtocol.RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inputSize);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException();
            if (AcousticEchoCanceler.isAvailable()) {
                echo = AcousticEchoCanceler.create(record.getAudioSessionId());
                if (echo != null) echo.setEnabled(true);
            }
            int outputSize = Math.max(9600, AudioTrack.getMinBufferSize(RealtimeProtocol.RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT));
            track = new AudioTrack.Builder().setAudioAttributes(TalkAudio.speech(true))
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RealtimeProtocol.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(outputSize).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException();
            track.play();
            record.startRecording();
            new Thread(() -> capture(input, error), "jarvis-openai-mic").start();
            new Thread(() -> playback(error), "jarvis-openai-audio").start();
        } catch (Exception e) { close(); error.accept("OpenAI-Mikrofon oder Audioausgabe konnte nicht gestartet werden."); }
    }

    private void capture(Consumer<String> input, Consumer<String> error) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        byte[] buffer = new byte[960]; // 20 ms, little-endian PCM16 mono
        try {
            while (!closed) {
                int n = record.read(buffer, 0, buffer.length);
                if (n <= 0) { if (!closed) error.accept("Das OpenAI-Mikrofon wurde unterbrochen."); return; }
                if (!closed) input.accept(Base64.encodeToString(buffer, 0, n, Base64.NO_WRAP));
            }
        } catch (Exception e) { if (!closed) error.accept("Das OpenAI-Mikrofon ist nicht mehr verfügbar."); }
    }

    void add(String base64) {
        if (closed) return;
        byte[] data = Base64.decode(base64, Base64.DEFAULT);
        synchronized (lock) {
            if (closed) return;
            if ((data.length & 1) != 0 || !queue.offer(new Chunk(data, epoch))) throw new IllegalStateException("Audiopuffer voll");
        }
    }

    private void playback(Consumer<String> error) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        try {
            while (!closed) {
                Chunk chunk = queue.poll(20, TimeUnit.MILLISECONDS);
                byte[] data = chunk == null ? null : chunk.data;
                int offset = 0;
                while (data != null && offset < data.length && !closed) {
                    int n;
                    synchronized (lock) {
                        if (chunk.epoch != epoch) break;
                        n = track.write(data, offset, data.length - offset, AudioTrack.WRITE_NON_BLOCKING);
                        if (n < 0) throw new IllegalStateException();
                        writtenSamples += n / 2;
                    }
                    offset += n;
                    if (n == 0) Thread.sleep(5);
                }
                Runnable callback = null;
                synchronized (lock) {
                    if (!closed && queue.isEmpty() && drained != null && playedSamples() >= writtenSamples) {
                        callback = drained; drained = null;
                    }
                }
                if (callback != null) callback.run();
            }
        } catch (Exception e) { if (!closed) error.accept("Die OpenAI-Audioausgabe wurde unterbrochen."); }
    }

    void whenDrained(Runnable callback) { synchronized (lock) { drained = callback; } }

    int interrupt() {
        synchronized (lock) {
            int playedMs = (int) (Math.min(writtenSamples, playedSamples()) * 1000 / RealtimeProtocol.RATE);
            epoch++; queue.clear(); drained = null;
            if (track != null && !closed) {
                track.pause(); track.flush();
                headBase = Integer.toUnsignedLong(track.getPlaybackHeadPosition());
                track.play();
            }
            writtenSamples = 0;
            return playedMs;
        }
    }

    private long playedSamples() {
        if (track == null || closed) return 0;
        return Math.max(0, Integer.toUnsignedLong(track.getPlaybackHeadPosition()) - headBase);
    }

    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true; epoch++; queue.clear(); drained = null;
            if (record != null) { try { record.stop(); } catch (Exception ignored) {} record.release(); }
            if (echo != null) echo.release();
            if (track != null) { try { track.pause(); track.flush(); } catch (Exception ignored) {} track.release(); }
        }
    }
}
