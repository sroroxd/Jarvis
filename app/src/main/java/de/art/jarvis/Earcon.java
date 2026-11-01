package de.art.jarvis;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Kurzer, weicher Zwei-Ton („Ich höre“) – selbst erzeugt, damit keine Audiodatei nötig ist.
 * Wichtig im Spielmodus, wo nichts auf dem Bildschirm erscheint.
 */
final class Earcon {
    private static final int RATE = 24000;
    private final short[] samples = build();
    private AudioTrack track;

    void play() {
        try {
            if (track == null) {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(RATE)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(samples.length * 2)
                        .build();
                track.write(samples, 0, samples.length);
            }
            if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) track.stop();
            track.reloadStaticData();
            track.play();
        } catch (Exception ignored) {
            // Ton ist nur ein Hinweis – ohne geht es auch.
        }
    }

    void release() {
        if (track != null) {
            track.release();
            track = null;
        }
    }

    /** Zwei kurze Sinustöne (E6 → A6) mit sanftem Ein- und Ausblenden, zusammen ca. 0,18 s. */
    private static short[] build() {
        double[][] notes = {{1318.5, 0.075}, {1760.0, 0.105}};
        int total = 0;
        for (double[] n : notes) total += (int) (n[1] * RATE);
        short[] out = new short[total];
        int pos = 0;
        for (double[] n : notes) {
            int len = (int) (n[1] * RATE);
            for (int i = 0; i < len; i++) {
                double t = i / (double) RATE;
                double attack = Math.min(1.0, i / (RATE * 0.008));
                double release = Math.min(1.0, (len - i) / (RATE * 0.04));
                double env = attack * release;
                double v = Math.sin(2 * Math.PI * n[0] * t) * 0.6 + Math.sin(4 * Math.PI * n[0] * t) * 0.08;
                out[pos + i] = (short) (v * env * 0.28 * Short.MAX_VALUE);
            }
            pos += len;
        }
        return out;
    }
}
