package de.art.jarvis;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * Liest eine WAV-Datei als Gleitkomma-Samples (mono, −1 … 1) – für die Sprachprobe der Offline-Stimme
 * ({@link PocketVoice}). Kann 16-bit-PCM und 32-bit-float, mono oder mehrkanalig (wird gemittelt).
 * Reines Java, daher automatisch testbar.
 */
final class Wav {
    final float[] samples;
    final int rate;

    private Wav(float[] samples, int rate) {
        this.samples = samples;
        this.rate = rate;
    }

    static Wav read(File f) throws IOException {
        return parse(Files.readAllBytes(f.toPath()));
    }

    static Wav parse(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) { // "RIFF" … "WAVE"
            throw new IOException("keine WAV-Datei");
        }
        int format = -1, channels = 0, rate = 0, bits = 0;
        int pos = 12;
        while (pos + 8 <= data.length) {
            int id = b.getInt(pos);
            int size = b.getInt(pos + 4);
            int body = pos + 8;
            if (size < 0 || body + size > data.length) size = data.length - body; // abgeschnittene Datei: Rest nehmen
            if (id == 0x20746d66) { // "fmt "
                format = b.getShort(body) & 0xffff;
                channels = b.getShort(body + 2) & 0xffff;
                rate = b.getInt(body + 4);
                bits = b.getShort(body + 14) & 0xffff;
                if (format == 0xfffe && size >= 26) format = b.getShort(body + 24) & 0xffff; // WAVE_FORMAT_EXTENSIBLE
            } else if (id == 0x61746164) { // "data"
                if (channels <= 0 || rate <= 0) throw new IOException("WAV ohne Formatangabe");
                return new Wav(samples(b, body, size, format, channels, bits), rate);
            }
            pos = body + size + (size & 1);
        }
        throw new IOException("WAV ohne Daten");
    }

    private static float[] samples(ByteBuffer b, int start, int size, int format, int channels, int bits)
            throws IOException {
        boolean pcm16 = format == 1 && bits == 16;
        boolean float32 = format == 3 && bits == 32;
        if (!pcm16 && !float32) throw new IOException("WAV-Format nicht unterstützt (" + format + "/" + bits + " bit)");
        int bytes = bits / 8;
        int frames = size / (bytes * channels);
        float[] out = new float[frames];
        for (int i = 0; i < frames; i++) {
            float sum = 0;
            for (int c = 0; c < channels; c++) {
                int at = start + (i * channels + c) * bytes;
                sum += pcm16 ? b.getShort(at) / 32768f : b.getFloat(at);
            }
            out[i] = sum / channels;
        }
        return out;
    }
}
