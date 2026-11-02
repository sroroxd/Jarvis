package de.art.jarvis;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Herz von Jarvis: hört auf „Hey Jarvis“ (oder Antippen), sieht den Bildschirm,
 * fragt Claude und spricht die Antwort.
 *
 * <p>Sichtbar ist nur, was eingeschaltet ist: das schwebende J (Standard aus) und die
 * nicht anklickbaren Untertitel. Im Spielmodus verdeckt nichts das Spielbild – Jarvis antwortet nur per Stimme.
 */
public class JarvisService extends Service {
    public static final String ACTION_START = "de.art.jarvis.START";
    public static final String ACTION_LISTEN = "de.art.jarvis.LISTEN";
    public static final String ACTION_RESET = "de.art.jarvis.RESET";
    public static final String ACTION_REFRESH = "de.art.jarvis.REFRESH";
    public static final String ACTION_STOP = "de.art.jarvis.STOP";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_DATA = "data";

    private static final String CHANNEL = "jarvis";
    private static final int NOTIFICATION_ID = 1;
    private static final long CONVERSATION_TIMEOUT_MS = 15 * 60 * 1000L;
    private static final int MAX_HISTORY = 16;
    private static final long CAPTION_LINGER_MS = 4000;

    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    /** Für den Startbildschirm. Alle Aufrufe kommen auf dem Haupt-Thread. */
    public interface UiListener {
        void onStateChanged(State state);

        void onLevel(float level);

        void onExchange(String you, String jarvis);

        void onStatusChanged();
    }

    // ---------------------------------------------------------------- Zustand für die Oberfläche

    private static volatile boolean running;
    private static State uiState = State.IDLE;
    private static UiListener ui;
    private static String lastYou = "";
    private static String lastJarvis = "";
    private static volatile String lastTiming = "";
    private static String lastModel = "";
    private final Timing timing = new Timing();
    private static boolean screenShared;
    private static String wakeProblem;
    private static boolean screenAwake = true;

    public static boolean isRunning() {
        return running;
    }

    static State currentState() {
        return uiState;
    }

    static String lastYou() {
        return lastYou;
    }

    static String lastJarvis() {
        return lastJarvis;
    }

    /** Zeitmessung der letzten Antwort, z. B. „Zuhören 0,4 s · KI 1,8 s · Stimme 0,6 s · gesamt 2,8 s“. */
    static String lastTiming() {
        return lastTiming;
    }

    static boolean isScreenShared() {
        return running && screenShared;
    }

    /** Fehlertext der Wachwort-Erkennung oder {@code null}. */
    static String wakeProblem() {
        return wakeProblem;
    }

    static boolean isScreenAwake() {
        return screenAwake;
    }

    static void setUiListener(UiListener listener) {
        ui = listener;
    }

    // ---------------------------------------------------------------- Felder

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<JSONObject> history = new ArrayList<>();
    private final StringBuilder utterance = new StringBuilder();

    private State state = State.IDLE;
    private Prefs prefs;
    private ClaudeClient claude;
    private GeminiClient gemini;
    /** Websuche und Kalender – nur im Alltag mit Claude (der Spielmodus bleibt unverändert schnell). */
    private Tools tools;
    private ElevenTts eleven;
    private boolean elevenWarned;
    private WindowManager wm;
    private MediaProjection projection;
    private ScreenGrabber grabber;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String ttsEngine;
    private String appliedVoiceKey;
    private String pendingSpeech;
    private long lastInteraction;
    private boolean heardSomething;
    private boolean finishRequested;
    /** Jede Anfrage bekommt eine Nummer; Antworten auf überholte Anfragen werden verworfen. */
    private int requestId;

    private FloatingJ floatingJ;
    private Subtitles subtitles;
    private WakeWordEngine wake;
    private Earcon earcon;
    private long wakeRetryMs = 2000;
    private boolean wakeErrorSpoken;
    private boolean receiverRegistered;

    /** Nach der Sprechpause ist niemand mehr zu hören → abschicken. */
    private final Runnable pauseElapsed = () -> {
        if (state != State.LISTENING) return;
        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {
                // egal
            }
        }
        finishListening();
    };

    /** Nach dem Zuhören das Wachwort wieder scharf schalten (kurz warten, bis die Spracherkennung das Mikrofon frei gibt). */
    private final Runnable resumeWake = () -> {
        if (state != State.LISTENING) updateWake();
    };

    private final Runnable retryWake = () -> {
        wakeProblem = null;
        updateWake();
        notifyUi();
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            screenAwake = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            updateWake();
            notifyUi();
        }
    };

    // ---------------------------------------------------------------- Lebenszyklus

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        claude = new ClaudeClient();
        gemini = new GeminiClient();
        tools = new Tools(this);
        eleven = new ElevenTts(this);
        earcon = new Earcon();
        subtitles = new Subtitles(this);
        wm = getSystemService(WindowManager.class);
        wake = new WakeWordEngine(this, new WakeWordEngine.Listener() {
            @Override
            public void onWakeWord() {
                JarvisService.this.onWakeWord();
            }

            @Override
            public void onWakeError(String message) {
                JarvisService.this.onWakeError(message);
            }
        });
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Jarvis", NotificationManager.IMPORTANCE_LOW));
        screenAwake = getSystemService(PowerManager.class).isInteractive();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, filter);
        receiverRegistered = true;
        initTts(Voices.resolveEngine(this, prefs));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            @SuppressWarnings("deprecation")
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            int code = intent.getIntExtra(EXTRA_CODE, 0);
            boolean withScreen = projection != null || data != null;
            int types = 0;
            if (withScreen) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            // Mikrofon-Typ ist nötig, damit Jarvis auch über anderen Apps zuhören darf.
            if (micGranted()) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            if (types == 0) {
                stopSelf();
                return START_NOT_STICKY;
            }
            boolean wasRunning = running;
            startForeground(NOTIFICATION_ID, buildNotification(), types);
            running = true;
            if (projection == null && data != null) startProjection(code, data);
            applySettings();
            if (!wasRunning) main.postDelayed(() -> say("Zu Diensten, Sir."), 500);
            return START_NOT_STICKY;
        }
        if (!running) {
            // Ohne Start (z. B. alte Benachrichtigung) nichts tun.
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_LISTEN.equals(action)) onTap();
        else if (ACTION_RESET.equals(action)) resetConversation();
        else if (ACTION_REFRESH.equals(action)) applySettings();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        screenShared = false;
        main.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        wake.release();
        destroyRecognizer();
        eleven.stop();
        earcon.release();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (floatingJ != null) floatingJ.remove();
        floatingJ = null;
        subtitles.destroy();
        if (grabber != null) grabber.stop();
        grabber = null;
        if (projection != null) projection.stop();
        projection = null;
        state = State.IDLE;
        uiState = State.IDLE;
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (ui != null) {
            ui.onStateChanged(State.IDLE);
            ui.onStatusChanged();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Übernimmt geänderte Einstellungen (J, Untertitel, Wachwort, Spielmodus) sofort. */
    private void applySettings() {
        boolean showJ = prefs.floatingJ() && !prefs.tftMode() && Settings.canDrawOverlays(this);
        if (showJ && floatingJ == null) {
            floatingJ = new FloatingJ(this, prefs, new FloatingJ.Callbacks() {
                @Override public void onTap() { JarvisService.this.onTap(); }
                @Override public void onLongPress() { resetConversation(); }
            });
            floatingJ.show(state);
        } else if (!showJ && floatingJ != null) {
            floatingJ.remove();
            floatingJ = null;
        }
        if (!subtitlesWanted()) subtitles.hide();
        wake.setSensitivity(prefs.sensitivity());
        wakeProblem = null;
        wakeRetryMs = 2000;
        main.removeCallbacks(retryWake);
        updateWake();
        updateNotification();
        maybeUpdateMeta();
        notifyUi();
    }

    // ---------------------------------------------------------------- Tägliche TFT-Meta

    private boolean metaRunning;
    private long metaLastAttempt;

    /**
     * Holt im Spielmodus einmal am Tag die aktuelle Meta aus dem Internet (im Hintergrund, die Frage wartet nicht).
     * Nach einem Fehlschlag frühestens nach 30 Minuten erneut.
     */
    private void maybeUpdateMeta() {
        if (metaRunning || !prefs.tftMode() || prefs.isGemini() || prefs.apiKey().isEmpty()) return;
        final String today = today();
        if (Meta.isFresh(prefs.metaDate(), today)) return;
        long now = SystemClock.elapsedRealtime();
        if (metaLastAttempt != 0 && now - metaLastAttempt < 30 * 60 * 1000L) return;
        metaLastAttempt = now;
        metaRunning = true;
        claude.sendWithWebSearch(prefs.apiKey(), Meta.MODEL, Meta.system(), Meta.prompt(today), Meta.MAX_SEARCHES,
                new ClaudeClient.Callback() {
                    @Override
                    public void onResult(String text) {
                        metaRunning = false;
                        prefs.setMeta(text, today);
                        notifyUi();
                    }

                    @Override
                    public void onError(String message) {
                        metaRunning = false;
                    }
                });
    }

    static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY).format(new java.util.Date());
    }

    private boolean micGranted() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    // ---------------------------------------------------------------- Bildschirm

    private void startProjection(int code, Intent data) {
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        try {
            projection = mpm.getMediaProjection(code, data);
        } catch (Exception e) {
            projection = null;
        }
        if (projection == null) {
            screenShared = false;
            return;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                main.post(() -> {
                    if (grabber != null) {
                        grabber.stop();
                        grabber = null;
                    }
                    projection = null;
                    screenShared = false;
                    notifyUi();
                });
            }
        }, main);
        Rect b = wm.getCurrentWindowMetrics().getBounds();
        grabber = new ScreenGrabber(projection);
        grabber.start(b.width(), b.height(), getResources().getDisplayMetrics().densityDpi);
        screenShared = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (grabber != null) {
            Rect b = wm.getCurrentWindowMetrics().getBounds();
            grabber.resize(b.width(), b.height(), getResources().getDisplayMetrics().densityDpi);
        }
    }

    // ---------------------------------------------------------------- Anzeige

    private boolean subtitlesWanted() {
        if (!Settings.canDrawOverlays(this)) return false;
        return prefs.tftMode() ? prefs.subtitlesInGame() : prefs.subtitles();
    }

    /** Kurzer Zwischenstand („Ich höre …“, Mitschrift). Nur im Alltag – im Spielmodus bleibt der Bildschirm frei. */
    private void status(String text) {
        if (prefs.tftMode() || !subtitlesWanted() || ui != null) return; // in der eigenen App steht alles schon da
        subtitles.show(text, false, prefs.subtitlesTop(), 0);
    }

    /** Wie {@link #status}, blendet sich aber nach ein paar Sekunden selbst aus. */
    private void statusBriefly(String text) {
        if (prefs.tftMode() || !subtitlesWanted() || ui != null) return;
        subtitles.show(text, false, prefs.subtitlesTop(), CAPTION_LINGER_MS);
    }

    /** Antworttext als Untertitel (im Spielmodus nur, wenn ausdrücklich eingeschaltet). */
    private void caption(String text) {
        if (!subtitlesWanted() || ui != null) return;
        subtitles.show(text, prefs.tftMode(), prefs.subtitlesTop(), 0);
    }

    private void setCaptureHidden(boolean hidden) {
        if (floatingJ != null) floatingJ.setCaptureHidden(hidden);
        subtitles.setCaptureHidden(hidden);
    }

    private void setState(State s) {
        State old = state;
        state = s;
        uiState = s;
        if (floatingJ != null) floatingJ.setState(s);
        if (ui != null) ui.onStateChanged(s);
        if (old == State.LISTENING && s != State.LISTENING) {
            main.removeCallbacks(resumeWake);
            main.postDelayed(resumeWake, 350);
        }
    }

    private void notifyUi() {
        if (ui != null) ui.onStatusChanged();
    }

    private void publishExchange(String you, String jarvis) {
        if (you != null) lastYou = you;
        if (jarvis != null) lastJarvis = jarvis;
        if (ui != null) ui.onExchange(lastYou, lastJarvis);
    }

    // ---------------------------------------------------------------- Wachwort „Hey Jarvis“

    private void updateWake() {
        boolean should = running && prefs.wakeWord() && micGranted() && screenAwake
                && state != State.LISTENING && wakeProblem == null;
        if (should) wake.start();
        else wake.stop(null);
    }

    private void onWakeWord() {
        if (!running) return;
        wakeRetryMs = 2000;
        switch (state) {
            case LISTENING:
                return;
            case SPEAKING:
                stopSpeaking();
                break;
            case THINKING:
                requestId++; // laufende Antwort verwerfen – Art hat eine neue Frage
                break;
            default:
                break;
        }
        startListening();
    }

    private void onWakeError(String message) {
        wakeProblem = message;
        notifyUi();
        if (!wakeErrorSpoken) {
            wakeErrorSpoken = true;
            statusBriefly("„Hey Jarvis“ pausiert: " + message);
        }
        main.removeCallbacks(retryWake);
        main.postDelayed(retryWake, wakeRetryMs);
        wakeRetryMs = Math.min(30000, wakeRetryMs * 2);
    }

    // ---------------------------------------------------------------- Ablauf

    /** Antippen (Reaktor, J, Benachrichtigung, Seitentaste). */
    private void onTap() {
        switch (state) {
            case LISTENING:
                // Antippen beim Zuhören = „fertig, abschicken“.
                finishRequested = true;
                if (recognizer != null) {
                    recognizer.stopListening();
                    main.removeCallbacks(pauseElapsed);
                    main.postDelayed(pauseElapsed, 2500); // Sicherheitsnetz
                } else {
                    finishListening();
                }
                break;
            case THINKING:
                statusBriefly("Einen Moment, Sir – ich denke noch nach …");
                break;
            case SPEAKING:
                stopSpeaking();
                startListening();
                break;
            default:
                startListening();
        }
    }

    private void resetConversation() {
        history.clear();
        requestId++;
        stopSpeaking();
        if (state == State.LISTENING) {
            main.removeCallbacks(pauseElapsed);
            releaseRecognizer();
            utterance.setLength(0);
        }
        setState(State.IDLE);
        publishExchange("", "");
        say("Neues Gespräch.");
    }

    private void startListening() {
        if (!micGranted()) {
            say("Mir fehlt die Mikrofon-Berechtigung. Bitte öffne die Jarvis-App.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Auf diesem Handy ist keine Spracherkennung verfügbar.");
            return;
        }
        utterance.setLength(0);
        finishRequested = false;
        heardSomething = false;
        timing.reset();
        setState(State.LISTENING);
        if (prefs.earcon()) earcon.play();
        if (prefs.tftMode()) subtitles.hide(); // alte Antwort weg, Spielbild frei
        status("Ich höre …");
        // Erst das Wachwort-Mikrofon freigeben, dann übernimmt die Spracherkennung.
        wake.stop(() -> {
            if (state == State.LISTENING) beginRecognition();
        });
    }

    /**
     * Ein Erkennungsdurchgang. Nach jedem erkannten Satzteil startet sofort der nächste,
     * damit Art Pausen machen kann. Abgeschickt wird erst, wenn nach einem Satzteil
     * prefs.pauseMs() lang niemand spricht – oder wenn Art erneut antippt.
     */
    private void beginRecognition() {
        releaseRecognizer();
        final SpeechRecognizer current = createRecognizer();
        recognizer = current;
        current.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (current != recognizer) return;
                if (utterance.length() == 0) {
                    status("Ich höre, Sir …");
                } else {
                    status(utterance + " …");
                    main.removeCallbacks(pauseElapsed);
                    main.postDelayed(pauseElapsed, prefs.pauseMs());
                }
            }

            @Override
            public void onBeginningOfSpeech() {
                if (current != recognizer) return;
                heardSomething = true;
                if (!finishRequested) main.removeCallbacks(pauseElapsed);
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                if (current != recognizer || ui == null) return;
                ui.onLevel(Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f)));
            }

            @Override public void onBufferReceived(byte[] buffer) { }
            @Override
            public void onEndOfSpeech() {
                if (current == recognizer) timing.speechEnded(SystemClock.elapsedRealtime());
            }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override
            public void onPartialResults(Bundle partial) {
                if (current != recognizer) return;
                String text = firstResult(partial);
                if (!text.isEmpty()) {
                    status(joined(text) + " …");
                    publishExchange(joined(text), "");
                }
            }

            @Override
            public void onResults(Bundle results) {
                if (current != recognizer) return;
                main.removeCallbacks(pauseElapsed);
                String text = firstResult(results);
                if (!text.isEmpty()) {
                    if (utterance.length() > 0) utterance.append(' ');
                    utterance.append(text);
                }
                releaseRecognizer();
                // Fertiger Satz → sofort abschicken. Nur wenn der Satz offensichtlich mittendrin endet
                // („… und“, „ich wollte fragen ob“), hört Jarvis weiter zu. Früher wurde IMMER weitergehört,
                // das kostete bei jeder Frage rund 2,5 Sekunden.
                if (finishRequested || utterance.length() > 1500 || !Text.looksUnfinished(utterance.toString())) {
                    finishListening();
                    return;
                }
                // Weiterhören – es war nur eine Denkpause.
                main.postDelayed(() -> {
                    if (state != State.LISTENING) return;
                    if (finishRequested) finishListening();
                    else beginRecognition();
                }, 150);
            }

            @Override
            public void onError(int error) {
                if (current != recognizer) return;
                main.removeCallbacks(pauseElapsed);
                releaseRecognizer();
                if (utterance.length() > 0) {
                    finishListening(); // Stille nach dem Sprechen = fertig
                    return;
                }
                setState(State.IDLE);
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    // Nach „Hey Jarvis“ ohne Frage einfach still zurück in Bereitschaft.
                    statusBriefly(heardSomething ? "Ich habe dich nicht verstanden." : "Ich habe nichts gehört.");
                } else {
                    String msg = "Die Spracherkennung meldet Fehler " + error + ".";
                    if (prefs.tftMode()) say(msg);
                    else statusBriefly(msg + " Versuch es einfach nochmal.");
                }
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        // Sprechpause aus den Einstellungen. Keine erzwungene Mindestdauer mehr – die hielt früher
        // auch kurze Fragen wie „Wie spät ist es?“ mindestens 2 Sekunden fest.
        long silence = prefs.pauseMs();
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence);
        try {
            current.startListening(i);
        } catch (Exception e) {
            releaseRecognizer();
            if (utterance.length() > 0) {
                finishListening();
            } else {
                setState(State.IDLE);
                say("Die Spracherkennung konnte nicht starten.");
            }
        }
    }

    private String joined(String partial) {
        return utterance.length() == 0 ? partial : utterance + " " + partial;
    }

    /** Alles Gesagte zusammen abschicken. */
    private void finishListening() {
        main.removeCallbacks(pauseElapsed);
        releaseRecognizer();
        if (state != State.LISTENING) return;
        timing.speechEndedIfUnknown(SystemClock.elapsedRealtime());
        String text = utterance.toString().trim();
        utterance.setLength(0);
        finishRequested = false;
        if (text.isEmpty()) {
            setState(State.IDLE);
            statusBriefly("Ich habe nichts verstanden.");
            return;
        }
        publishExchange(text, "");
        handleUtterance(text);
    }

    /** Gibt den aktuellen Erkenner frei, ohne einen gerade neu gestarteten zu treffen. */
    private void releaseRecognizer() {
        final SpeechRecognizer r = recognizer;
        recognizer = null;
        if (r != null) {
            main.post(() -> {
                try {
                    r.destroy();
                } catch (Exception ignored) {
                    // bereits freigegeben
                }
            });
        }
    }

    /** Bevorzugt Googles Spracherkennung – die von Samsung lehnt Anfragen anderer Apps oft ab. */
    private SpeechRecognizer createRecognizer() {
        ComponentName google = new ComponentName("com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService");
        try {
            getPackageManager().getServiceInfo(google, 0);
            return SpeechRecognizer.createSpeechRecognizer(this, google);
        } catch (PackageManager.NameNotFoundException e) {
            return SpeechRecognizer.createSpeechRecognizer(this);
        }
    }

    private static String firstResult(Bundle b) {
        if (b == null) return "";
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty() || list.get(0) == null) return "";
        return list.get(0).trim();
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
                // bereits freigegeben
            }
            recognizer = null;
        }
    }

    private void handleUtterance(String text) {
        maybeUpdateMeta();
        // „Spielmodus an/aus“ erledigt die App selbst.
        Boolean game = Commands.gameMode(text);
        if (game != null) {
            prefs.setTftMode(game);
            Backup.autoSave(this, prefs);
            setState(State.IDLE);
            applySettings();
            speak(game ? "Spielmodus aktiv. Ich bleibe unsichtbar und antworte nur per Stimme."
                    : "Spielmodus beendet.");
            return;
        }
        // „Merk dir …“ / „Vergiss …“ ebenso – zuverlässig und ohne Kosten.
        Memory.Outcome local = Memory.command(text, prefs.memory());
        if (local != null) {
            prefs.setMemory(local.memory);
            Backup.autoSave(this, prefs);
            setState(State.IDLE);
            speak(local.reply);
            return;
        }
        String key = prefs.apiKey();
        if (key.isEmpty()) {
            setState(State.IDLE);
            say("Mir fehlt noch der API-Schlüssel. Bitte trag ihn in den Jarvis-Einstellungen ein.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastInteraction > CONVERSATION_TIMEOUT_MS) history.clear();
        lastInteraction = now;

        final int id = ++requestId;
        setState(State.THINKING);
        status("„" + text + "“");
        if (grabber == null) {
            ask(id, key, text, null);
            return;
        }
        boolean overlayVisible = floatingJ != null || subtitles.isShowing();
        if (!overlayVisible) {
            // Nichts eingeblendet (z. B. Spielmodus) → sofort fotografieren, keine Wartezeit.
            ask(id, key, text, grabber.captureJpegBase64());
            return;
        }
        // Eingeblendetes kurz verstecken, damit es nicht auf dem Bildschirmfoto landet.
        setCaptureHidden(true);
        main.postDelayed(() -> {
            String image = grabber == null ? null : grabber.captureJpegBase64();
            setCaptureHidden(false);
            if (id == requestId) ask(id, key, text, image);
        }, 120); // 120 ms reichen, damit das Bild ohne Einblendung neu gezeichnet ist (vorher 300 ms)
    }

    private void ask(int id, String key, String text, String image) {
        JSONArray messages = new JSONArray();
        try {
            for (JSONObject m : history) messages.put(m);
            JSONArray content = new JSONArray();
            if (image != null) {
                content.put(new JSONObject()
                        .put("type", "image")
                        .put("source", new JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", image)));
            }
            content.put(new JSONObject().put("type", "text").put("text", text));
            messages.put(new JSONObject().put("role", "user").put("content", content));
        } catch (JSONException e) {
            setState(State.IDLE);
            say("Interner Fehler beim Vorbereiten der Anfrage.");
            return;
        }

        // Werkzeuge (Websuche, Kalender) nur im Alltag mit Claude – der Spielmodus bleibt schnell und nutzt die Tages-Meta.
        boolean withTools = !prefs.tftMode() && !prefs.isGemini();
        String system = Persona.system(prefs.tftMode(), image != null, prefs.memory(), prefs.metaText(), prefs.metaDate(),
                withTools);
        ClaudeClient.Callback callback = new ClaudeClient.Callback() {
            @Override
            public void onResult(String answer) {
                timing.answered(SystemClock.elapsedRealtime());
                if (id != requestId || !running) return; // überholt (neue Frage, neues Gespräch)
                Memory.Outcome tagged = Memory.tags(answer, prefs.memory());
                prefs.setMemory(tagged.memory);
                Backup.autoSave(JarvisService.this, prefs);
                answer = Text.replyOrFallback(answer, Text.cleanForSpeech(tagged.reply));
                remember("user", image != null ? "[Bildschirmfoto war angehängt] " + text : text);
                remember("assistant", answer);
                speak(answer);
            }

            @Override
            public void onError(String message) {
                if (id != requestId || !running) return;
                setState(State.IDLE);
                say(message);
            }
        };
        timing.sent(SystemClock.elapsedRealtime());
        lastModel = prefs.model();
        if (prefs.isGemini()) gemini.send(key, prefs.model(), system, messages, callback);
        // Nachdenken nach Bedarf: Bei einfachen Fragen antwortet Claude sofort, bei kniffligen denkt er kurz nach
        // (so wie der allererste Jarvis). Im Spielmodus etwas gründlicher und ohne Werkzeuge; im Alltag mit Werkzeugen.
        else claude.send(key, prefs.model(), system, messages, prefs.tftMode() ? "medium" : "low",
                withTools ? tools : null, callback);
    }

    private void remember(String role, String text) {
        try {
            history.add(new JSONObject().put("role", role).put("content", text));
        } catch (JSONException ignored) {
            return;
        }
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            history.remove(0);
        }
    }

    // ---------------------------------------------------------------- Sprechen

    /** Startet die Sprachausgabe mit der gewählten Engine (z. B. SherpaTTS). */
    private void initTts(String engine) {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
                // alte Instanz bereits weg
            }
        }
        ttsReady = false;
        ttsEngine = engine;
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = Voices.create(this, engine, status -> {
            if (holder[0] != tts) return; // veraltete Instanz
            if (status != TextToSpeech.SUCCESS) {
                if (engine != null) initTts(null); // Engine streikt → Systemstandard
                return;
            }
            ttsReady = true;
            Voices.apply(tts, prefs, engine);
            appliedVoiceKey = null; // neue Engine → beim nächsten Satz sicher neu einstellen
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { main.post(JarvisService.this::onVoiceStarted); }
                @Override public void onDone(String id) { main.post(JarvisService.this::onSpeechFinished); }
                @Override public void onError(String id) { main.post(JarvisService.this::onSpeechFinished); }
            });
            if (pendingSpeech != null) {
                String text = pendingSpeech;
                pendingSpeech = null;
                speakNow(text);
            }
        });
        tts = holder[0];
    }

    private void say(String text) {
        speak(text);
    }

    private void speak(String text) {
        String clean = Text.cleanForSpeech(text);
        if (clean.isEmpty()) {
            onSpeechFinished();
            return;
        }
        caption(clean);
        publishExchange(null, clean);
        // Premium-Stimme (ElevenLabs), wenn eingerichtet – sonst die Stimme des Handys.
        if (!prefs.elevenKey().isEmpty()) {
            speakPremium(clean);
            return;
        }
        speakDevice(clean);
    }

    private void speakPremium(String clean) {
        setState(State.SPEAKING);
        eleven.speak(prefs.elevenKey(), prefs.elevenVoice(), clean, prefs.rate(), new ElevenTts.Listener() {
            @Override public void onStart() { onVoiceStarted(); }

            @Override
            public void onDone() {
                onSpeechFinished();
            }

            @Override
            public void onError(String message) {
                // Einmal pro Sitzung Bescheid geben, dann mit der Handy-Stimme weitermachen.
                if (!elevenWarned) {
                    elevenWarned = true;
                    statusBriefly(message + " Ich spreche mit der Handy-Stimme weiter.");
                }
                speakDevice(clean);
            }
        });
    }

    /** Erster Ton einer Antwort: Messung abschließen und auf dem Startbildschirm zeigen. */
    private void onVoiceStarted() {
        if (timing.voiceStarted(SystemClock.elapsedRealtime())) {
            lastTiming = timing.summary() + " (" + lastModel + ")";
            notifyUi();
        }
    }

    private void stopSpeaking() {
        pendingSpeech = null;
        if (tts != null) tts.stop();
        eleven.stop();
    }

    private void speakDevice(String clean) {
        // Wurde in den Einstellungen eine andere Engine gewählt? Dann neu starten und danach sprechen.
        String engineNow = Voices.resolveEngine(this, prefs);
        boolean engineChanged = engineNow == null ? ttsEngine != null : !engineNow.equals(ttsEngine);
        if (engineChanged || !ttsReady) {
            pendingSpeech = clean;
            setState(State.SPEAKING);
            if (engineChanged) initTts(engineNow);
            main.postDelayed(() -> {
                if (pendingSpeech != null) { // Sprachausgabe kam nicht hoch
                    pendingSpeech = null;
                    onSpeechFinished();
                }
            }, 6000);
            return;
        }
        speakNow(clean);
    }

    private void speakNow(String clean) {
        int max = TextToSpeech.getMaxSpeechInputLength() - 1;
        String text = clean.length() > max ? clean.substring(0, max) : clean;
        setState(State.SPEAKING);
        // Stimme nur neu einstellen, wenn sich etwas geändert hat – das Durchsuchen aller Stimmen
        // vor jedem Satz kostete spürbar Zeit.
        String voiceKey = ttsEngine + "|" + prefs.voiceName() + "|" + prefs.rate() + "|" + prefs.pitch();
        if (!voiceKey.equals(appliedVoiceKey)) {
            Voices.apply(tts, prefs, ttsEngine);
            appliedVoiceKey = voiceKey;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-" + System.nanoTime());
    }

    private void onSpeechFinished() {
        if (state == State.SPEAKING) setState(State.IDLE);
        subtitles.hideAfter(CAPTION_LINGER_MS);
    }

    // ---------------------------------------------------------------- Benachrichtigung

    private void updateNotification() {
        if (!running) return;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, JarvisService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent listen = PendingIntent.getService(this, 3,
                new Intent(this, JarvisService.class).setAction(ACTION_LISTEN), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        String name = BuildConfig.TEST_APP ? "Jarvis Test" : "Jarvis";
        String title = prefs.tftMode() ? name + " ist aktiv · Spielmodus" : name + " ist aktiv";
        String text = prefs.wakeWord()
                ? "Sag „Hey Jarvis“ – oder tippe auf „Sprechen“."
                : "Tippe auf „Sprechen“, um mit Jarvis zu reden.";
        Icon icon = Icon.createWithResource(this, R.drawable.ic_jarvis);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_jarvis)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(icon, "Sprechen", listen).build())
                .addAction(new Notification.Action.Builder(icon, "Beenden", stop).build())
                .build();
    }
}
