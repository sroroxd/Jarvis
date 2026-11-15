package de.art.jarvis;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ein bewusster Start, höchstens zehn Minuten/sechs Antworten, keine automatische Wiederverbindung. */
final class OpenAiSession implements AutoCloseable {
    interface Frames {
        ScreenGrabber.FreshFrame capture(long requestedAt);
        boolean allowed();
    }
    interface Ui {
        void state(JarvisService.State state, String message);
        void answer(String text);
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService captures = Executors.newSingleThreadExecutor();
    private final Frames frames;
    private final Ui ui;
    private final RealtimeAudio audio = new RealtimeAudio();
    private final OpenAiRealtimeClient client;
    private boolean closed;
    private boolean ready;
    private int epoch;
    private int turns;
    private long tokens;
    private final StringBuilder transcript = new StringBuilder();
    private final Runnable timeout = () -> stop("OpenAI-Test nach zehn Minuten beendet.");
    private final Runnable answerTimeout = () -> stop("OpenAI hat nicht rechtzeitig geantwortet. Sitzung beendet.");

    OpenAiSession(Frames frames, Ui ui) {
        this.frames = frames; this.ui = ui;
        client = new OpenAiRealtimeClient(new OpenAiRealtimeClient.Listener() {
            @Override public void ready() { main.post(() -> {
                if (closed) return;
                ready = true;
                main.removeCallbacks(answerTimeout);
                audio.start(client::appendAudio, message -> main.post(() -> stop(message)));
                if (!closed) ui.state(JarvisService.State.LISTENING, info("OpenAI hört zu. Bild nur auf Frage oder Knopfdruck."));
            }); }
            @Override public void speechStarted() { main.post(() -> {
                if (closed) return;
                epoch++; main.removeCallbacks(answerTimeout);
                client.interrupt(audio.interrupt()); transcript.setLength(0);
                ui.state(JarvisService.State.LISTENING, info("Antwort unterbrochen. OpenAI hört zu."));
            }); }
            @Override public void turnCommitted() { main.post(() -> ask(false)); }
            @Override public void audio(String base64) { main.post(() -> {
                if (closed) return;
                try { audio.add(base64); ui.state(JarvisService.State.SPEAKING, info("OpenAI spricht · Losreden unterbricht.")); }
                catch (Exception e) { stop("OpenAI-Audiopuffer überlastet. Sitzung beendet."); }
            }); }
            @Override public void transcript(String text) { main.post(() -> { if (!closed) transcript.append(text); }); }
            @Override public void responseDone(long input, long output) { main.post(() -> {
                if (closed) return;
                main.removeCallbacks(answerTimeout); tokens += input + output;
                if (transcript.length() > 0) ui.answer(transcript.toString());
                transcript.setLength(0);
                audio.whenDrained(() -> main.post(() -> {
                    if (closed) return;
                    if (turns >= RealtimeProtocol.MAX_TURNS) stop("OpenAI-Test nach sechs Antworten beendet. " + tokens + " Tokens verbraucht.");
                    else ui.state(JarvisService.State.LISTENING, info("OpenAI hört zu."));
                }));
            }); }
            @Override public void error(String message) { main.post(() -> stop(message)); }
        });
    }

    void start(String key) {
        ui.state(JarvisService.State.THINKING, "Verbinde OpenAI Realtime · " + RealtimeProtocol.MODEL);
        main.postDelayed(timeout, RealtimeProtocol.MAX_SESSION_MS);
        // Auch ein hängender Token-Endpunkt/Handshake darf das Mikrofon nicht unbemerkt offen lassen.
        main.postDelayed(answerTimeout, 30000);
        client.start(key);
    }

    void ask(boolean manual) {
        if (closed) return;
        if (!ready) { ui.state(JarvisService.State.THINKING, "OpenAI wird noch verbunden."); return; }
        if (turns >= RealtimeProtocol.MAX_TURNS) { stop("OpenAI-Testlimit erreicht."); return; }
        if (manual) client.interrupt(audio.interrupt());
        int requestEpoch = ++epoch;
        long requestedAt = SystemClock.elapsedRealtime();
        transcript.setLength(0);
        ui.state(JarvisService.State.THINKING, info("Prüfe ein frisches, freigegebenes Bild …"));
        captures.execute(() -> {
            ScreenGrabber.FreshFrame candidate;
            try { candidate = frames.capture(requestedAt); } catch (Exception e) { candidate = null; }
            final ScreenGrabber.FreshFrame frame = candidate;
            main.post(() -> {
                if (closed || requestEpoch != epoch) return;
                boolean fresh = frames.allowed() && frame != null && RealtimeProtocol.fresh(frame.at, requestedAt, SystemClock.elapsedRealtime());
                turns++;
                client.respond(fresh ? frame.jpeg : null, fresh);
                main.removeCallbacks(answerTimeout); main.postDelayed(answerTimeout, 45000);
                ui.state(JarvisService.State.THINKING, info(fresh ? "Aktuelles Bild an OpenAI gesendet." : "Kein frisches Bild: Nicht sicher erkennbar."));
            });
        });
    }

    private String info(String message) {
        return message + "\nKostenpflichtig · " + RealtimeProtocol.MODEL + " · Antworten " + turns + "/6 · Tokens " + tokens
                + "\nKein festes Geldlimit. Keine unbeaufsichtigte Bildübertragung oder Ausweichmodelle.";
    }

    void stop(String reason) {
        if (closed) return;
        close(); ui.state(JarvisService.State.IDLE, reason + " Zum Neustart den Kern antippen.");
    }

    boolean isClosed() { return closed; }

    void screenRevoked() {
        if (closed) return;
        epoch++;
        client.interrupt(audio.interrupt()); client.forgetImage();
        ui.state(JarvisService.State.LISTENING, info("Bildschirmfreigabe beendet. Spielstand: Nicht sicher erkennbar."));
    }

    @Override public void close() {
        if (closed) return;
        closed = true; ready = false; epoch++;
        main.removeCallbacksAndMessages(null);
        client.close(); audio.close(); captures.shutdownNow();
    }
}
