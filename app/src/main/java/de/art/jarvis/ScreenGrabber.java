package de.art.jarvis;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Hält den Bildschirm-Mitschnitt offen und liefert auf Abruf das aktuelle Bild als JPEG (Base64).
 *
 * <p>Der Mitschnitt läuft verkleinert ({@link CaptureSize#MIRROR_EDGE}) statt in voller Auflösung (3120 × 1440):
 * Das System skaliert beim Spiegeln, jedes Einzelbild ist rund 2,6-mal kleiner – spürbar weniger Akku und
 * Wärme während einer langen TFT-Partie. Für ein Foto wird nur noch höchstens auf die Alltagsgröße verkleinert.
 */
final class ScreenGrabber {

    private final MediaProjection projection;
    private final Object lock = new Object();
    private HandlerThread thread;
    private Handler handler;
    private VirtualDisplay display;
    private ImageReader reader;
    private Image latest;
    private int width;
    private int height;
    /**
     * Wiederverwendete Arbeitsflächen für {@link #toBitmap} – nur unter {@link #lock} angefasst. Vorher legte jedes
     * Foto einen rund 7 MB großen Puffer und ein gepolstertes Bitmap neu an; im Spiel alle 6 Sekunden. Das kostete
     * Wärme und Müllsammeln. Jetzt bleibt beides stehen und wird nur bei einer anderen Größe (Drehung) neu gebaut.
     */
    private ByteBuffer copyScratch;
    private Bitmap paddedScratch;

    ScreenGrabber(MediaProjection projection) {
        this.projection = projection;
    }

    /** Startet den Mitschnitt; w/h/dpi sind die echten Bildschirmmaße, verkleinert wird hier. */
    void start(int w, int h, int dpi) {
        thread = new HandlerThread("jarvis-capture");
        thread.start();
        handler = new Handler(thread.getLooper());
        int[] s = CaptureSize.fit(w, h, CaptureSize.MIRROR_EDGE);
        reader = newReader(s[0], s[1]);
        display = projection.createVirtualDisplay("jarvis-screen", s[0], s[1], CaptureSize.scaledDpi(dpi, w, s[0]),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
    }

    /** Bei Drehung (z. B. TFT im Querformat) das Abbild an die neue Größe anpassen. */
    void resize(int w, int h, int dpi) {
        if (handler == null) return;
        final int[] s = CaptureSize.fit(w, h, CaptureSize.MIRROR_EDGE);
        final int scaledDpi = CaptureSize.scaledDpi(dpi, w, s[0]);
        handler.post(() -> {
            if (display == null || (s[0] == width && s[1] == height)) return;
            ImageReader old = reader;
            synchronized (lock) {
                if (latest != null) {
                    latest.close();
                    latest = null;
                }
                // Andere Größe: die große Arbeitsfläche freigeben, toBitmap baut sie beim nächsten Bild neu.
                if (paddedScratch != null) {
                    paddedScratch.recycle();
                    paddedScratch = null;
                }
            }
            reader = newReader(s[0], s[1]);
            display.resize(s[0], s[1], scaledDpi);
            display.setSurface(reader.getSurface());
            old.close();
        });
    }

    private ImageReader newReader(int w, int h) {
        width = w;
        height = h;
        ImageReader r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
        r.setOnImageAvailableListener(rd -> {
            Image img;
            try {
                img = rd.acquireLatestImage();
            } catch (IllegalStateException e) {
                return;
            }
            if (img == null) return;
            synchronized (lock) {
                if (latest != null) latest.close();
                latest = img;
            }
        }, handler);
        return r;
    }

    /**
     * Aktuelles Bild als JPEG in Base64 oder null, wenn noch keins da ist.
     * game = Spielmodus: schärfer (größer, weniger Kompression), damit TFT-Details lesbar bleiben.
     */
    String captureJpegBase64(boolean game) {
        byte[] jpeg = captureJpeg(game);
        return jpeg == null ? null : Base64.encodeToString(jpeg, Base64.NO_WRAP);
    }

    /** Wie {@link #captureJpegBase64}, aber als rohe JPEG-Bytes. */
    byte[] captureJpeg(boolean game) {
        Bitmap bitmap;
        synchronized (lock) {
            if (latest == null) return null;
            try {
                bitmap = toBitmap(latest);
            } catch (Exception e) {
                return null;
            }
        }
        Bitmap scaled = scale(bitmap, CaptureSize.edge(game));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, CaptureSize.quality(game), out);
        if (scaled != bitmap) scaled.recycle();
        bitmap.recycle();
        return out.toByteArray();
    }

    /** Nur unter {@link #lock} aufrufen – nutzt die wiederverwendeten Arbeitsflächen. Gibt ein eigenes Bitmap zurück,
     *  das der Aufrufer freigeben darf; die Arbeitsflächen bleiben stehen. */
    private Bitmap toBitmap(Image img) {
        Image.Plane plane = img.getPlanes()[0];
        ByteBuffer src = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int w = img.getWidth();
        int h = img.getHeight();
        int paddedWidth = rowStride / pixelStride;
        int needed = rowStride * h; // = paddedWidth * pixelStride * h

        src.rewind();
        ByteBuffer buf;
        if (src.remaining() >= needed) {
            // Mitschnitt-Puffer ist vollständig: direkt daraus lesen, ohne eine zweite ~7-MB-Kopie.
            buf = src;
        } else {
            // Letzte Zeile ohne Padding: in den wiederverwendeten Puffer kopieren (keine Allokation je Bild). Die
            // fehlenden Bytes liegen in der Polster-Spalte der letzten Zeile und werden gleich weggeschnitten.
            if (copyScratch == null || copyScratch.capacity() < needed) copyScratch = ByteBuffer.allocate(needed);
            copyScratch.clear();
            int count = Math.min(src.remaining(), needed);
            ByteBuffer slice = src.duplicate();
            slice.limit(slice.position() + count);
            copyScratch.put(slice);
            copyScratch.rewind();
            buf = copyScratch;
        }

        // Gepolstertes Bitmap stehen lassen und wiederverwenden; nur bei anderer Größe neu bauen.
        if (paddedScratch == null || paddedScratch.getWidth() != paddedWidth || paddedScratch.getHeight() != h) {
            if (paddedScratch != null) paddedScratch.recycle();
            paddedScratch = Bitmap.createBitmap(paddedWidth, h, Bitmap.Config.ARGB_8888);
        }
        paddedScratch.copyPixelsFromBuffer(buf);
        // Immer ein eigenes Bitmap (w × h) zurückgeben; paddedScratch bleibt die Arbeitsfläche.
        return Bitmap.createBitmap(paddedScratch, 0, 0, w, h);
    }

    private static Bitmap scale(Bitmap b, int maxEdge) {
        int w = b.getWidth();
        int h = b.getHeight();
        if (Math.max(w, h) <= maxEdge) return b;
        int[] s = CaptureSize.fit(w, h, maxEdge);
        return Bitmap.createScaledBitmap(b, s[0], s[1], true);
    }

    void stop() {
        synchronized (lock) {
            if (latest != null) {
                latest.close();
                latest = null;
            }
            if (paddedScratch != null) {
                paddedScratch.recycle();
                paddedScratch = null;
            }
            copyScratch = null;
        }
        if (display != null) {
            display.release();
            display = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        handler = null;
    }
}
