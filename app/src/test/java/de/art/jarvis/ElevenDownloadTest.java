package de.art.jarvis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Echte Warteschlange und absichtlich blockierende Fake-Verbindungen, ohne Netz oder API-Schlüssel. */
public class ElevenDownloadTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private ExecutorService worker;

    @Before public void startWorker() {
        worker = Executors.newSingleThreadExecutor();
    }

    @After public void stopWorker() throws Exception {
        worker.shutdownNow();
        assertTrue("Test-Thread muss beendet werden", worker.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test public void verworfenerWartenderAuftragSendetKeineAnfrage() throws Exception {
        CountDownLatch workerBusy = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        worker.submit(() -> {
            workerBusy.countDown();
            awaitUninterruptibly(releaseWorker);
        });
        assertTrue(workerBusy.await(2, TimeUnit.SECONDS));
        AtomicInteger opens = new AtomicInteger();
        FakeConnection conn = new FakeConnection();
        Probe probe = new Probe();
        ElevenDownload request = new ElevenDownload();
        try {
            request.start(worker, () -> {
                opens.incrementAndGet();
                return conn;
            }, bytes("alter Satz"), temp.getRoot(), probe);
            request.cancel();
        } finally {
            releaseWorker.countDown();
        }
        drainWorker();
        assertEquals(0, opens.get());
        assertEquals(0, conn.posts.get());
        assertEquals(0, probe.callbacks.get());
        assertEquals(0, temp.getRoot().list().length);
    }

    @Test public void abbrechenLoestNetzWartenUndLaesstNaechstenSatzDurch() throws Exception {
        FakeConnection slow = new FakeConnection();
        slow.blockResponse = true;
        Probe obsolete = new Probe();
        ElevenDownload first = new ElevenDownload();
        first.start(worker, () -> slow, bytes("alt"), temp.getRoot(), obsolete);
        try {
            assertTrue(slow.responseEntered.await(2, TimeUnit.SECONDS));
            first.cancel();
            FakeConnection fast = new FakeConnection();
            fast.audio = bytes("neuer Ton");
            Probe latest = new Probe();
            new ElevenDownload().start(worker, () -> fast, bytes("neu"), temp.getRoot(), latest);
            assertTrue("Nicht den alten Netz-Timeout abwarten", latest.finished.await(2, TimeUnit.SECONDS));
            assertTrue(slow.disconnects.get() > 0);
            assertEquals(0, obsolete.callbacks.get());
            assertNotNull(latest.file);
            assertArrayEquals(bytes("neuer Ton"), Files.readAllBytes(latest.file.toPath()));
            assertArrayEquals(bytes("neu"), fast.sent.toByteArray());
        } finally {
            first.cancel();
            // Auch bei einer fehlgeschlagenen Assertion keinen blockierten Test-Thread zurücklassen.
            slow.disconnected.countDown();
        }
    }

    @Test public void abbruchWaehrendDownloadLoeschtTeilDateiUndMeldetKeinenFehler() throws Exception {
        FakeConnection conn = new FakeConnection();
        conn.blockBody = true;
        Probe probe = new Probe();
        ElevenDownload request = new ElevenDownload();
        request.start(worker, () -> conn, bytes("Satz"), temp.getRoot(), probe);
        try {
            assertTrue(conn.bodyBlocked.await(2, TimeUnit.SECONDS));
            assertEquals(1, temp.getRoot().list().length);
            request.cancel();
            drainWorker();
            assertEquals(0, temp.getRoot().list().length);
            assertEquals(0, probe.callbacks.get());
            assertTrue(conn.disconnects.get() > 0);
        } finally {
            request.cancel();
            conn.disconnected.countDown();
        }
    }

    @Test public void abbruchZwischenErzeugenUndAnhaengenSchliesstVerbindungOhnePost() throws Exception {
        FakeConnection conn = new FakeConnection();
        CountDownLatch creating = new CountDownLatch(1);
        CountDownLatch allowAttach = new CountDownLatch(1);
        Probe probe = new Probe();
        ElevenDownload request = new ElevenDownload();
        request.start(worker, () -> {
            creating.countDown();
            awaitUninterruptibly(allowAttach);
            return conn;
        }, bytes("Satz"), temp.getRoot(), probe);
        try {
            assertTrue(creating.await(2, TimeUnit.SECONDS));
            request.cancel();
        } finally {
            allowAttach.countDown();
        }
        drainWorker();
        assertEquals(0, conn.posts.get());
        assertTrue(conn.disconnects.get() > 0);
        assertEquals(0, probe.callbacks.get());
    }

    @Test public void antwortenHabenGetrennteDateienUndUeberschreibenKeinenLaufendenTon() throws Exception {
        FakeConnection first = new FakeConnection();
        first.audio = bytes("erster Ton wird noch abgespielt");
        Probe firstProbe = new Probe();
        new ElevenDownload().start(worker, () -> first, bytes("eins"), temp.getRoot(), firstProbe);
        assertTrue(firstProbe.finished.await(2, TimeUnit.SECONDS));
        FakeConnection second = new FakeConnection();
        second.audio = bytes("zweiter Ton");
        Probe secondProbe = new Probe();
        new ElevenDownload().start(worker, () -> second, bytes("zwei"), temp.getRoot(), secondProbe);
        assertTrue(secondProbe.finished.await(2, TimeUnit.SECONDS));
        assertNotNull(firstProbe.file);
        assertNotNull(secondProbe.file);
        assertNotEquals(firstProbe.file, secondProbe.file);
        assertArrayEquals(first.audio, Files.readAllBytes(firstProbe.file.toPath()));
        assertArrayEquals(second.audio, Files.readAllBytes(secondProbe.file.toPath()));
    }

    @Test public void httpFehlerBleibtAuswertbarOhneAudiodatei() throws Exception {
        FakeConnection conn = new FakeConnection();
        conn.code = 429;
        conn.error = bytes("quota aufgebraucht");
        Probe probe = new Probe();
        new ElevenDownload().start(worker, () -> conn, bytes("Satz"), temp.getRoot(), probe);
        assertTrue(probe.finished.await(2, TimeUnit.SECONDS));
        assertEquals(429, probe.code);
        assertEquals("quota aufgebraucht", probe.body);
        assertEquals(1, probe.callbacks.get());
        assertEquals(0, temp.getRoot().list().length);
        assertFalse(probe.failed);
    }

    private void drainWorker() throws Exception {
        worker.submit(() -> { }).get(2, TimeUnit.SECONDS);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static final class Probe implements ElevenDownload.Listener {
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicInteger callbacks = new AtomicInteger();
        File file;
        int code;
        String body;
        boolean failed;

        @Override public void onAudio(File result) {
            file = result;
            callbacks.incrementAndGet();
            finished.countDown();
        }

        @Override public void onHttpError(int status, String text) {
            code = status;
            body = text;
            callbacks.incrementAndGet();
            finished.countDown();
        }

        @Override public void onFailure() {
            failed = true;
            callbacks.incrementAndGet();
            finished.countDown();
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        final AtomicInteger posts = new AtomicInteger();
        final AtomicInteger disconnects = new AtomicInteger();
        final CountDownLatch responseEntered = new CountDownLatch(1);
        final CountDownLatch bodyBlocked = new CountDownLatch(1);
        final CountDownLatch disconnected = new CountDownLatch(1);
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        boolean blockResponse;
        boolean blockBody;
        int code = 200;
        byte[] audio = bytes("Ton");
        byte[] error = new byte[0];

        FakeConnection() throws IOException {
            super(new URL("https://example.invalid/fake-tts"));
        }

        @Override public OutputStream getOutputStream() {
            posts.incrementAndGet();
            return sent;
        }

        @Override public int getResponseCode() throws IOException {
            responseEntered.countDown();
            if (blockResponse) {
                // Interrupt allein reicht absichtlich nicht; disconnect muss den Netzaufruf lösen.
                awaitUninterruptibly(disconnected);
                throw new IOException("Verbindung geschlossen");
            }
            return code;
        }

        @Override public InputStream getInputStream() {
            if (!blockBody) return new ByteArrayInputStream(audio);
            return new InputStream() {
                private boolean first = true;

                @Override public int read() throws IOException {
                    byte[] one = new byte[1];
                    return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
                }

                @Override public int read(byte[] b, int off, int len) throws IOException {
                    if (first) {
                        first = false;
                        b[off] = 1;
                        return 1;
                    }
                    bodyBlocked.countDown();
                    awaitUninterruptibly(disconnected);
                    throw new IOException("Verbindung geschlossen");
                }
            };
        }

        @Override public InputStream getErrorStream() {
            return new ByteArrayInputStream(error);
        }

        @Override public void disconnect() {
            disconnects.incrementAndGet();
            disconnected.countDown();
        }

        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
