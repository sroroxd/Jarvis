package de.art.jarvis;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;

/** Spielt Gleitkomma-Samples (mono) einmal ab und wartet, bis sie durch sind – für die Offline-Stimme. */
final class PcmPlayer {
    private PcmPlayer() {}

    /** Blockiert, bis das Audio gespielt ist. Nicht auf dem Haupt-Thread aufrufen. */
    static void play(float[] samples, int rate) {
        if (samples.length == 0) return;
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.length * 4)
                .build();
        try {
            track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
            track.play();
            SystemClock.sleep(samples.length * 1000L / rate + 100);
        } finally {
            track.release();
        }
    }
}
