package de.art.jarvis;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;

/**
 * Rein lokale GGUF-Inferenz. load/generate/close synchron auf einem Worker aufrufen;
 * TokenListener wird auf diesem Worker aufgerufen. cancel darf vom UI-Thread kommen.
 * Der Aufrufer liefert den vollstaendigen, bereits formatierten Chat-Prompt.
 */
final class LocalLlmEngine implements AutoCloseable {
    public interface TokenListener { void onToken(String text); }

    private static final boolean AVAILABLE;
    static {
        boolean loaded;
        try {
            System.loadLibrary("jarvis_llm");
            loaded = true;
        } catch (LinkageError | SecurityException e) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    private final Object operations = new Object();
    // Never hold this lock while loading or decoding; cancel must remain responsive.
    private final Object handleLock = new Object();
    private long handle;
    private boolean closed;
    private boolean generating;
    private volatile boolean cancelled;
    private volatile boolean tokenLimitReached;

    static boolean isAvailable() { return AVAILABLE; }

    void load(String path, int threads, int contextTokens) {
        if (!AVAILABLE) throw new IllegalStateException("Lokale KI ist auf diesem Geraet nicht verfuegbar.");
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("GGUF-Dateipfad fehlt.");
        if (threads < 1 || threads > 16 || contextTokens < 128 || contextTokens > 32768)
            throw new IllegalArgumentException("Ungueltige Threadzahl oder Kontextgroesse.");
        synchronized (operations) {
            checkOpen();
            checkCancelled();
            if (generating) throw new IllegalStateException("Modellwechsel waehrend einer Antwort ist nicht erlaubt.");
            releaseHandle();
            final long next;
            synchronized (handleLock) {
                checkCancelled();
                next = nativeCreate();
                handle = next;
            }
            try {
                // Publish the empty native state first so cancel can abort model loading.
                nativeLoadInto(next, path.getBytes(StandardCharsets.UTF_8), threads, contextTokens);
                checkCancelled();
            } catch (RuntimeException | Error e) {
                releaseHandle();
                checkCancelled();
                throw e;
            }
        }
    }

    String generate(String prompt, int maxTokens, TokenListener listener) {
        if (prompt == null || prompt.isEmpty()) throw new IllegalArgumentException("Die Anfrage ist leer.");
        if (maxTokens < 1 || maxTokens > 32768) throw new IllegalArgumentException("Ungueltige Antwortlaenge.");
        synchronized (operations) {
            checkOpen();
            if (generating) throw new IllegalStateException("Eine Antwort wird bereits erzeugt.");
            final long current;
            synchronized (handleLock) {
                checkCancelled();
                current = handle;
                if (current == 0) throw new IllegalStateException("Zuerst eine GGUF-Datei laden.");
            }
            generating = true;
            tokenLimitReached = false;
            try {
                String result = nativeGenerate(current, prompt.getBytes(StandardCharsets.UTF_8), maxTokens, listener);
                checkCancelled();
                tokenLimitReached = nativeTokenLimitReached(current);
                return result;
            } finally {
                generating = false;
            }
        }
    }

    void cancel() {
        synchronized (handleLock) {
            // Latched even without a native handle, including cancellation during load.
            // A new request uses a new LocalLlmEngine instance.
            cancelled = true;
            if (handle != 0) nativeCancel(handle);
        }
    }

    /** True after a successful partial answer that exhausted its token/context budget. */
    boolean wasTokenLimitReached() { return tokenLimitReached; }

    @Override public void close() {
        cancel();
        synchronized (operations) {
            if (generating) throw new IllegalStateException("close nicht aus dem TokenListener aufrufen.");
            closed = true;
            releaseHandle();
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Die lokale KI wurde geschlossen.");
    }

    private void checkCancelled() {
        if (cancelled) throw new CancellationException("Lokale Antwort abgebrochen.");
    }

    private void releaseHandle() {
        synchronized (handleLock) {
            if (handle != 0) {
                nativeClose(handle);
                handle = 0;
            }
        }
    }

    private static native long nativeCreate();
    private static native void nativeLoadInto(long handle, byte[] path, int threads, int contextTokens);
    private static native String nativeGenerate(long handle, byte[] prompt, int maxTokens, TokenListener listener);
    private static native boolean nativeTokenLimitReached(long handle);
    private static native void nativeCancel(long handle);
    private static native void nativeClose(long handle);
}
