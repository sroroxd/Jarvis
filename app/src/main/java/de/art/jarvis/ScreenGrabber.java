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

    private static Bitmap toBitmap(Image img) {
        Image.Plane plane = img.getPlanes()[0];
        ByteBuffer src = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int w = img.getWidth();
        int h = img.getHeight();
        int paddedWidth = rowStride / pixelStride;

        // Puffer auf volle Größe bringen – die letzte Zeile hat oft kein Padding.
        ByteBuffer dst = ByteBuffer.allocate(paddedWidth * pixelStride * h);
        src.rewind();
        int count = Math.min(src.remaining(), dst.capacity());
        ByteBuffer slice = src.duplicate();
        slice.limit(slice.position() + count);
        dst.put(slice);
        dst.rewind();

        Bitmap padded = Bitmap.createBitmap(paddedWidth, h, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(dst);
        if (paddedWidth == w) return padded;
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, w, h);
        padded.recycle();
        return cropped;
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
