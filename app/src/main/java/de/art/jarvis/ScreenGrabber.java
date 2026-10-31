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

/** Hält den Bildschirm-Mitschnitt offen und liefert auf Abruf das aktuelle Bild als JPEG (Base64). */
final class ScreenGrabber {
    private static final int MAX_EDGE = 1280;

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

    void start(int w, int h, int dpi) {
        thread = new HandlerThread("jarvis-capture");
        thread.start();
        handler = new Handler(thread.getLooper());
        reader = newReader(w, h);
        display = projection.createVirtualDisplay("jarvis-screen", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
    }

    /** Bei Drehung (z. B. TFT im Querformat) das Abbild an die neue Größe anpassen. */
    void resize(int w, int h, int dpi) {
        if (handler == null) return;
        handler.post(() -> {
            if (display == null || (w == width && h == height)) return;
            ImageReader old = reader;
            synchronized (lock) {
                if (latest != null) {
                    latest.close();
                    latest = null;
                }
            }
            reader = newReader(w, h);
            display.resize(w, h, dpi);
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

    /** Aktuelles Bild als JPEG in Base64 oder null, wenn noch keins da ist. */
    String captureJpegBase64() {
        Bitmap bitmap;
        synchronized (lock) {
            if (latest == null) return null;
            try {
                bitmap = toBitmap(latest);
            } catch (Exception e) {
                return null;
            }
        }
        Bitmap scaled = scale(bitmap);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out);
        if (scaled != bitmap) scaled.recycle();
        bitmap.recycle();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
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

    private static Bitmap scale(Bitmap b) {
        int w = b.getWidth();
        int h = b.getHeight();
        int longEdge = Math.max(w, h);
        if (longEdge <= MAX_EDGE) return b;
        float f = MAX_EDGE / (float) longEdge;
        return Bitmap.createScaledBitmap(b, Math.round(w * f), Math.round(h * f), true);
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
