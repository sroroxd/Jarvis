package de.art.jarvis;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Echte Android-Bildpuffer/JPEGs, aber keine Bildschirmfreigabe, Zugangsdaten oder API-Aufrufe. */
@RunWith(AndroidJUnit4.class)
public class ScreenGrabberTest {
    private ScreenGrabber grabber;
    private HandlerThread thread;
    private Handler handler;
    private Object lock;
    private ImageWriter writer;
    private final AtomicReference<Thread> captureThread = new AtomicReference<>();
    private final ExecutorService captures = Executors.newSingleThreadExecutor();

    @Before public void prepare() throws Exception {
        grabber = new ScreenGrabber(null);
        thread = new HandlerThread("capture-test");
        thread.start();
        handler = new Handler(thread.getLooper());
        lock = get("lock");
        set("thread", thread);
        set("handler", handler);
        ImageReader reader = newReader(64, 4);
        set("reader", reader);
        writer = ImageWriter.newInstance(reader.getSurface(), 2);
    }

    @After public void release() throws Exception {
        captures.shutdownNow();
        if (writer != null) writer.close();
        if (grabber != null) grabber.stop();
        if (thread != null) { thread.quitSafely(); thread.join(2000); }
    }

    @Test public void nachAnfrageEintreffendesBildWirdAbgewartet() throws Exception {
        long requestedAt = SystemClock.elapsedRealtime();
        Future<ScreenGrabber.FreshFrame> result = capture(requestedAt);
        assertWaiting(result);
        write(writer, Color.BLUE);
        ScreenGrabber.FreshFrame frame = result.get(3, TimeUnit.SECONDS);
        assertNotNull("Nicht voreilig ohne Bild antworten", frame);
        assertTrue(frame.at >= requestedAt);
        assertTrue(RealtimeProtocol.fresh(frame.at, requestedAt, SystemClock.elapsedRealtime()));
        assertBlue(frame);
    }

    @Test public void altesBildWirdDurchNeuenFrameErsetztStattNeuDatiert() throws Exception {
        write(writer, Color.RED);
        long oldAt = awaitFrame();
        waitUntilOld(oldAt);
        long requestedAt = SystemClock.elapsedRealtime();
        Future<ScreenGrabber.FreshFrame> result = capture(requestedAt);
        assertWaiting(result);
        write(writer, Color.BLUE);
        ScreenGrabber.FreshFrame frame = result.get(3, TimeUnit.SECONDS);
        assertNotNull(frame);
        assertTrue("Neuer Empfang statt neuer Zeit am alten Bild", frame.at > oldAt);
        assertTrue(RealtimeProtocol.fresh(frame.at, requestedAt, SystemClock.elapsedRealtime()));
        assertBlue(frame);
    }

    @Test public void statischesAltesBildBleibtNachBegrenztemWartenUnbrauchbar() throws Exception {
        write(writer, Color.RED);
        long oldAt = awaitFrame();
        waitUntilOld(oldAt);
        long requestedAt = SystemClock.elapsedRealtime();
        Future<ScreenGrabber.FreshFrame> result = capture(requestedAt);
        assertWaiting(result);
        assertNull("Ohne neuen Frame weiterhin kein frisches Bild", result.get(3, TimeUnit.SECONDS));
        synchronized (lock) { assertEquals(oldAt, ((Long) get("latestAt")).longValue()); }
    }

    @Test public void widerrufBeendetWartenSofortOhneBild() throws Exception {
        Future<ScreenGrabber.FreshFrame> result = capture(SystemClock.elapsedRealtime());
        assertWaiting(result);
        grabber.stop();
        assertNull("Stop weckt den Capture-Executor", result.get(500, TimeUnit.MILLISECONDS));
        assertNull(grabber.captureFresh(SystemClock.elapsedRealtime()));
    }

    @Test public void callbackVomErsetztenReaderWirdNichtAlsNeuesBildUebernommen() throws Exception {
        ImageReader old = (ImageReader) get("reader");
        ImageWriter oldWriter = writer;
        ImageReader replacement = newReader(32, 8);
        synchronized (lock) { set("reader", replacement); }
        writer = ImageWriter.newInstance(replacement.getSurface(), 2);
        try {
            CountDownLatch discarded = new CountDownLatch(1);
            oldWriter.setOnImageReleasedListener(ignored -> discarded.countDown(), handler);
            write(oldWriter, Color.RED);
            assertTrue("Alter Callback wird verarbeitet und sein Bild freigegeben", discarded.await(3, TimeUnit.SECONDS));
            synchronized (lock) {
                assertNull("Kein Frame aus der alten Ausrichtung", get("latest"));
                assertEquals(0L, ((Long) get("latestAt")).longValue());
            }
            Future<ScreenGrabber.FreshFrame> result = capture(SystemClock.elapsedRealtime());
            assertWaiting(result);
            write(writer, Color.BLUE);
            ScreenGrabber.FreshFrame frame = result.get(3, TimeUnit.SECONDS);
            assertNotNull(frame);
            Bitmap bitmap = decode(frame);
            try { assertEquals(32, bitmap.getWidth()); assertEquals(8, bitmap.getHeight()); }
            finally { bitmap.recycle(); }
            assertBlue(frame);
        } finally { oldWriter.close(); old.close(); }
    }

    @Test public void mehrereJpegsRecycelnDieWiederverwendeteArbeitsflaecheNicht() throws Exception {
        write(writer, Color.BLUE);
        awaitFrame();
        for (int i = 0; i < 3; i++) {
            byte[] jpeg = grabber.captureJpeg(true);
            assertNotNull("Auch Folge-Capture liefert JPEG", jpeg);
            Bitmap decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            try {
                assertNotNull(decoded);
                assertEquals(64, decoded.getWidth());
                assertTrue(Color.blue(decoded.getPixel(32, 2)) > 220);
            } finally { if (decoded != null) decoded.recycle(); }
            Bitmap scratch = (Bitmap) get("paddedScratch");
            assertFalse("Mutable Quelle bleibt trotz Vollbild-Ausschnitt erhalten", scratch.isRecycled());
        }
    }

    private Future<ScreenGrabber.FreshFrame> capture(long requestedAt) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Future<ScreenGrabber.FreshFrame> result = captures.submit(() -> {
            captureThread.set(Thread.currentThread());
            started.countDown();
            return grabber.captureFresh(requestedAt);
        });
        assertTrue("Capture-Executor hat die Anfrage begonnen", started.await(3, TimeUnit.SECONDS));
        return result;
    }

    private void assertWaiting(Future<?> result) throws Exception {
        long end = SystemClock.elapsedRealtime() + 750;
        while (SystemClock.elapsedRealtime() < end) {
            assertFalse("Capture muss auf einen neuen Frame warten", result.isDone());
            Thread worker = captureThread.get();
            if (worker.getState() == Thread.State.TIMED_WAITING) {
                for (StackTraceElement frame : worker.getStackTrace()) {
                    if (frame.getClassName().equals(ScreenGrabber.class.getName())
                            && frame.getMethodName().equals("captureFresh")) return;
                }
            }
            Thread.sleep(1);
        }
        fail("Capture wartet nicht in ScreenGrabber.captureFresh");
    }

    private long awaitFrame() throws Exception {
        synchronized (lock) {
            long end = SystemClock.elapsedRealtime() + 3000;
            while (get("latest") == null) {
                long remaining = end - SystemClock.elapsedRealtime();
                assertTrue("ImageReader hat einen Frame geliefert", remaining > 0);
                lock.wait(remaining);
            }
            return (Long) get("latestAt");
        }
    }

    private static void waitUntilOld(long at) throws Exception {
        long delay = at + 300 - SystemClock.elapsedRealtime();
        if (delay > 0) Thread.sleep(delay);
    }

    private static void write(ImageWriter destination, int color) {
        Image image = destination.dequeueInputImage();
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer pixels = plane.getBuffer();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int offset = y * plane.getRowStride() + x * plane.getPixelStride();
                    pixels.put(offset, (byte) Color.red(color));
                    pixels.put(offset + 1, (byte) Color.green(color));
                    pixels.put(offset + 2, (byte) Color.blue(color));
                    pixels.put(offset + 3, (byte) 255);
                }
            }
            destination.queueInputImage(image);
        } finally { image.close(); }
    }

    private static Bitmap decode(ScreenGrabber.FreshFrame frame) {
        byte[] jpeg = Base64.decode(frame.jpeg, Base64.NO_WRAP);
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        assertNotNull("JPEG kann gelesen werden", bitmap);
        return bitmap;
    }

    private static void assertBlue(ScreenGrabber.FreshFrame frame) {
        Bitmap bitmap = decode(frame);
        try {
            int pixel = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
            assertTrue("Aktueller blauer statt alter roter Frame", Color.blue(pixel) > 220 && Color.red(pixel) < 30);
        } finally { bitmap.recycle(); }
    }

    // Nur der MediaProjection-Start wird ersetzt; Callback, ImageReader, Buffer, Bitmap und JPEG sind echt.
    private ImageReader newReader(int width, int height) throws Exception {
        Method method = ScreenGrabber.class.getDeclaredMethod("newReader", int.class, int.class);
        method.setAccessible(true);
        return (ImageReader) method.invoke(grabber, width, height);
    }

    private Object get(String name) throws Exception {
        Field field = ScreenGrabber.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(grabber);
    }

    private void set(String name, Object value) throws Exception {
        Field field = ScreenGrabber.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(grabber, value);
    }
}
