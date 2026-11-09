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
 * Das Herz von Jarvis: hört durchgehend zu („Immer zuhören“, sonst nach Antippen), sieht den Bildschirm,
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
    /** Nur Debug-Builds: eine Frage so behandeln, als hätte Art sie gesprochen (Gesprächstest in der CI). */
    public static final String ACTION_TEST_ASK = "de.art.jarvis.TEST_ASK";
    /** Nur Debug-Builds: den Phasen-Wächter einmal mit einem Testbild fragen (CI prüft Modell und Anfrageformat). */
    public static final String ACTION_TEST_WATCH = "de.art.jarvis.TEST_WATCH";
    /** Nur Debug-Builds: Offline-Stimme (Pocket TTS) laden und drei Sätze sprechen – Machbarkeit, siehe testPocket(). */
    public static final String ACTION_TEST_POCKET = "de.art.jarvis.TEST_POCKET";
    public static final String EXTRA_TEXT = "text";
    /** Log-Kennung für den Gesprächstest in der CI – nur Debug-Builds schreiben hier etwas hinein. */
    static final String TEST_TAG = "JarvisTest";
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
    /** Messzeile des letzten Dazwischenreden-Durchgangs (Startbildschirm, Gerätetest). */
    private static volatile String lastBargeInReport = "";
    /** Ganzer Gesprächsverlauf für den Startbildschirm (überlebt das Schließen der App, solange Jarvis läuft). */
    private static final ChatLog chatLog = new ChatLog();
    /** Läuft gerade das Dauergespräch? Für die Anzeige auf dem Startbildschirm. */
    private static volatile boolean continuous;
    private static String lastModel = "";
    private final Timing timing = new Timing();
    private static boolean screenShared;
    private static boolean screenAwake = true;

    public static boolean isRunning() {
        return running;
    }

    static State currentState() {
        return uiState;
    }

    /** Zeitmessung der letzten Antwort, z. B. „Zuhören 0,4 s · KI 1,8 s · Stimme 0,6 s · gesamt 2,8 s“. */
    static java.util.List<ChatLog.Entry> log() {
        return chatLog.snapshot();
    }

    static boolean isContinuous() {
        return continuous;
    }

    static String bargeInReport() {
        return lastBargeInReport;
    }

    static boolean isScreenShared() {
        return running && screenShared;
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
    /** Websuche und Kalender – nur im Alltag mit Claude (der Spielmodus bleibt unverändert schnell). */
    private Tools tools;
    private AgentClient agent;
    /**
     * Dauergespräch: Ohne Wort endet es nach dieser Zeit von selbst – sonst liefe das
     * Mikrofon unbemerkt stundenlang (Akku, Privatsphäre). Eine TFT-Partie mit Pausen passt hinein.
     */
    private static final long CONTINUOUS_IDLE_MS = 15 * 60 * 1000L;
    /** Kurze Pause nach dem Sprechen, damit der Nachhall der Stimme nicht als Frage erkannt wird. */
    private static final long RELISTEN_DELAY_MS = 350;
    /** Zeitpunkt, an dem Art im Dauergespräch zuletzt etwas gesagt hat. */
    private long continuousLastSpeech;
    /** Aufeinanderfolgende Erkennungsfehler im Dauergespräch – für das Zurückweichen. */
    private int continuousErrors;
    private final Runnable relisten = this::relistenIfContinuous;
    /** Prüft regelmäßig, ob Jarvis zuhören sollte, es aber nicht tut (siehe {@link ListenRules}). */
    private final Runnable listenWatchdog = this::listenWatchdog;
    /** Wann Jarvis zuletzt fertig gesprochen hat (elapsedRealtime) – danach gilt das Gespräch eine Weile als „warm“. */
    private long lastExchangeAt;
    /** Nur CI-Gerätetest: die nächste Testfrage läuft wie bei offenem Mikrofon nach einer Gesprächspause. */
    private boolean testOpenMic;
    /** Bildschirmfotos für Fragen und Ansagen – nicht auf dem Haupt-Thread, sonst stocken Erkennung und Stimme. */
    private final java.util.concurrent.ExecutorService captureExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Dazwischenreden: lauscht, während Jarvis im Dauergespräch spricht. */
    private BargeIn bargeIn;
    /** Automatische Spielerkennung: TFT vorne → Spielmodus und Dauergespräch (Einstellung „TFT automatisch erkennen“). */
    private GameWatch gameWatch;
    /** Telefonmodus im Dauergespräch, damit die Echo-Unterdrückung Jarvis' eigene Stimme herausfiltert. */
    private TalkAudio talkAudio;
    /** Zuletzt an Sprachausgabe und ElevenLabs gegebene Einstellung (Telefonmodus ja/nein). */
    private boolean speechAsCall;
    /** Was die Spielerkennung selbst eingeschaltet hat – nur das nimmt sie beim Verlassen des Spiels zurück. */
    private boolean autoGameMode;
    private boolean autoConversation;
    /** Wünsche an die Entwicklung → GitHub-Issues. */
    private WishSender wishSender;
    /** Eigene Zählung für Aufträge an den Agenten – siehe beauftrageAgenten(). */
    private int agentJobId;
    /** Für welchen Agenten-Auftrag der Fortschritts-Wächter gerade läuft; -1 = keiner. */
    private int agentPollFor = -1;
    /** Zeitpunkt der letzten Zwischenansage (oder des Auftragsstarts), für den Abstand. */
    private long agentAnsageZeit;
    private ElevenTts eleven;
    /** Offline-Stimme (Pocket TTS), siehe {@link PocketSpeaker}; wird erst bei Bedarf erzeugt. */
    private PocketSpeaker pocket;
    private boolean elevenWarned;
    /** ElevenLabs hat in diesem Gespräch versagt – bis zum nächsten Gespräch bei der Handy-Stimme bleiben (Wunsch #62). */
    private boolean elevenFailed;
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
    /** Zuletzt angewendeter Modus – wechselt er (Alltag ↔ Spiel), beginnt ein frisches Gespräch. */
    private boolean appliedGameMode;

    private FloatingJ floatingJ;
    private Subtitles subtitles;
    private Earcon earcon;
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

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            screenAwake = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            updateGameWatch();
            updateProactive();
            updateAlwaysListen();
            notifyUi();
        }
    };

    // ---------------------------------------------------------------- Lebenszyklus

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        appliedGameMode = prefs.tftMode();
        claude = new ClaudeClient();
        // Tagessumme der Kosten: jede Anfrage zählt, auch Phasen-Wächter und Meta-Recherche (Arts Plan vom 30. Okt.).
        ClaudeClient.costListener = (kind, usd) -> {
            prefs.addCost(today(), kind, usd);
            // Grenze gerade überschritten: einmal am Tag auf dem Haupt-Thread ansagen und den Wächter anhalten.
            if (prefs.overCostLimit(today())) main.post(this::onCostLimit);
        };
        tools = new Tools(this);
        agent = new AgentClient();
        eleven = new ElevenTts(this);
        earcon = new Earcon();
        // Eigener „Jarvis“-Erkenner fürs Dazwischenreden: „Jarvis“ unterbricht, während Jarvis spricht.
        bargeIn = new BargeIn(this::onArtSpeaks, () -> WakeModel.load(getAssets()));
        wishSender = new WishSender();
        gameWatch = new GameWatch(this, this::onGameChanged);
        talkAudio = new TalkAudio(this);
        bargeIn.setReportListener(report -> {
            lastBargeInReport = report + " · Telefonmodus " + (talkAudio.active() ? "an" : "aus");
            testLog("dazwischenreden: " + lastBargeInReport);
            notifyUi();
        });
        subtitles = new Subtitles(this);
        wm = getSystemService(WindowManager.class);
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
            if (!wasRunning) {
                main.removeCallbacks(listenWatchdog);
                main.postDelayed(listenWatchdog, ListenRules.WATCHDOG_MS);
            }
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
        else if (ACTION_TEST_ASK.equals(action) && debuggable()) {
            String text = intent.getStringExtra(EXTRA_TEXT);
            if (text != null && !text.trim().isEmpty()) {
                // Genau der Weg, den eine erkannte Spracheingabe nimmt – nur ohne Mikrofon.
                testLog("frage: " + text.trim());
                if (intent.getBooleanExtra("offen", false)) {
                    testOpenMic = true;
                    lastExchangeAt = 0;
                }
                publishExchange(text.trim(), "");
                handleUtterance(text.trim());
            }
        }
        else if (ACTION_TEST_POCKET.equals(action) && debuggable()) testPocket();
        else if (ACTION_TEST_WATCH.equals(action) && debuggable()) {
            if (intent.getBooleanExtra("bilder", false)) testWatchFiles();
            else testWatch();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        ClaudeClient.costListener = null;
        continuous = false;
        screenShared = false;
        main.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        gameWatch.stop();
        watchExecutor.shutdown();
        captureExecutor.shutdown();
        talkAudio.exit();
        bargeIn.release();
        destroyRecognizer();
        eleven.stop();
        if (pocket != null) pocket.release();
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

    /** Übernimmt geänderte Einstellungen (J, Untertitel, Zuhören, Spielmodus) sofort. */
    private void applySettings() {
        // Moduswechsel (per Sprache, Schalter auf dem Startbildschirm oder in den Einstellungen) = neues Gespräch:
        // Alltagsgespräche gehören nicht ins TFT-Coaching – und Pläne der letzten Partie nicht in die nächste.
        if (prefs.tftMode() != appliedGameMode) {
            appliedGameMode = prefs.tftMode();
            history.clear();
        }
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
        updateGameWatch();
        updateTalkAudio();
        updateProactive();
        updateAlwaysListen();
        updateNotification();
        maybeUpdateMeta();
        notifyUi();
    }

    // ---------------------------------------------------------------- Immer zuhören (Wunsch #39/#47)

    /** Art hat das Zuhören per Antippen oder „Gespräch beenden“ pausiert – bis er wieder antippt. */
    private boolean listenPaused;

    /** Soll Jarvis gerade von selbst durchgehend zuhören? Nur bei eingeschaltetem Bildschirm (Akku). */
    private boolean listenWanted() {
        return running && prefs.alwaysListen() && micGranted() && screenAwake && !listenPaused;
    }

    /**
     * „Immer zuhören“: Statt auf ein Wachwort zu warten, läuft das Dauergespräch, solange Jarvis läuft und der
     * Bildschirm an ist. Bildschirm aus: Zuhören endet still, beim Einschalten geht es von selbst weiter.
     */
    private void updateAlwaysListen() {
        if (listenWanted()) {
            if (!continuous) {
                continuous = true;
                continuousLastSpeech = SystemClock.elapsedRealtime();
                continuousErrors = 0;
                notifyUi();
            }
            // Nicht sofort: Beim Start sagt Jarvis erst „Zu Diensten“; spricht er dann, ist das hier wirkungslos und
            // das Ende seiner Ansage startet das Zuhören (onSpeechFinished).
            if (state == State.IDLE) scheduleRelisten(1200);
        } else if (continuous && prefs.alwaysListen() && !screenAwake && !gameWatch.inGame()) {
            stopContinuous(false);
        }
    }

    /**
     * Wächter: Soll Jarvis zuhören, steht aber still da (kein Zuhören, Denken, Sprechen und kein Neustart geplant),
     * wird das Zuhören neu angeworfen. Vorher konnte „Immer zuhören“ nach Fehlern der Spracherkennung dauerhaft
     * taub bleiben, bis Art den Bildschirm aus- und wieder einschaltete.
     */
    private void listenWatchdog() {
        if (!running) return;
        main.postDelayed(listenWatchdog, ListenRules.WATCHDOG_MS);
        boolean wanted = listenWanted();
        if (!ListenRules.kickNeeded(wanted || continuous, continuous, state == State.IDLE, main.hasCallbacks(relisten))) {
            return;
        }
        testLog("waechter: zuhoeren neu angeworfen");
        if (wanted) updateAlwaysListen();
        else scheduleRelisten(RELISTEN_DELAY_MS);
    }

    // ---------------------------------------------------------------- Telefonmodus (Echo-Unterdrückung)

    /**
     * Im Dauergespräch mit Dazwischenreden läuft Jarvis' Stimme wie ein Videoanruf (siehe {@link TalkAudio}) – nur so
     * filtert das Handy sein Echo heraus, und Art kommt mit normaler Stimme darüber. Sonst wie bisher.
     */
    private void updateTalkAudio() {
        boolean want = running && continuous && prefs.bargeIn() && prefs.echoCallMode() && micGranted();
        boolean on;
        if (want) {
            on = talkAudio.enter();
        } else {
            talkAudio.exit();
            on = false;
        }
        if (on == speechAsCall) return;
        speechAsCall = on;
        if (tts != null) tts.setAudioAttributes(TalkAudio.speech(on));
        eleven.setVoiceCall(on);
    }

    // ---------------------------------------------------------------- Automatische Spielerkennung

    /** Läuft nur, wenn eingeschaltet, erlaubt und der Bildschirm an ist (spart Akku). */
    private void updateGameWatch() {
        if (running && screenAwake && prefs.gameAuto() && GameWatch.permitted(this)) gameWatch.start();
        else gameWatch.stop();
    }

    /**
     * TFT ist vorne: Spielmodus an und ohne Wachwort zuhören (Arts Wunsch vom 2. Nov.: „nicht ständig Jarvis
     * sagen, er soll erkennen, wenn ich im Spiel bin“). Verlassen: nur zurücknehmen, was hier eingeschaltet wurde.
     */
    private void onGameChanged(boolean inGame) {
        if (!running) return;
        if (inGame) {
            if (!prefs.tftMode()) {
                prefs.setTftMode(true);
                autoGameMode = true;
                applySettings();
            }
            if (!continuous && micGranted()) {
                continuous = true;
                autoConversation = true;
                continuousLastSpeech = SystemClock.elapsedRealtime();
                continuousErrors = 0;
                notifyUi();
                // Kurz Bescheid geben; danach hört das Dauergespräch von selbst zu.
                if (state == State.IDLE) speak("TFT erkannt. Ich höre zu, Sir.");
            }
        } else {
            // Mit „Immer zuhören“ bleibt das Zuhören nach dem Spiel an.
            if (autoConversation && continuous && !listenWanted()) stopContinuous(false);
            autoConversation = false;
            if (autoGameMode && prefs.tftMode()) {
                prefs.setTftMode(false);
                applySettings();
            }
            autoGameMode = false;
        }
    }

    // ---------------------------------------------------------------- Tägliche TFT-Meta

    // ---------------------------------------------------------------- Proaktives Coaching (Wunsch #41/#42)

    private final ProactiveCoach coachPolicy = new ProactiveCoach();
    private final java.util.concurrent.ExecutorService watchExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable proactiveTick = this::proactiveTick;
    private boolean proactiveScheduled;
    private boolean proactiveBusy;

    private boolean proactiveWanted() {
        return running && screenAwake && prefs.tftMode() && prefs.proactiveCoach() && grabber != null
                && !prefs.apiKey().isEmpty() && !prefs.overCostLimit(today());
    }

    /** Wächter an, wenn Spielmodus, Bildschirmfreigabe und Einstellung passen; sonst aus. */
    private void updateProactive() {
        if (proactiveWanted()) {
            if (!proactiveScheduled) {
                proactiveScheduled = true;
                main.postDelayed(proactiveTick, ProactiveCoach.CHECK_EVERY_MS);
            }
        } else {
            proactiveScheduled = false;
            main.removeCallbacks(proactiveTick);
        }
    }

    /** Ruhig genug für eine Ansage? Leerlauf oder Dauergespräch, in dem Art gerade nichts sagt. */
    private boolean quietForCoach() {
        return state == State.IDLE || (state == State.LISTENING && utterance.length() == 0 && !heardSomething);
    }

    private void proactiveTick() {
        if (!proactiveWanted()) {
            proactiveScheduled = false;
            return;
        }
        main.postDelayed(proactiveTick, ProactiveCoach.CHECK_EVERY_MS);
        if (proactiveBusy || !quietForCoach()) return;
        // Mit Spielerkennung: nur, wenn TFT wirklich vorne ist.
        if (prefs.gameAuto() && GameWatch.permitted(this) && !gameWatch.inGame()) return;
        proactiveBusy = true;
        final ScreenGrabber g = grabber;
        final String key = prefs.apiKey();
        // Bild umwandeln und verkleinern kostet auf dem S24 Ultra (3120 × 1440) spürbar Zeit – alle 6 s nicht auf
        // dem Haupt-Thread, sonst stocken Spracherkennung und Sprachausgabe.
        watchExecutor.execute(() -> {
            String image = g.captureJpegBase64(false); // Alltagsgröße reicht, um die Phase zu erkennen
            if (image == null || !sendWatch(key, image)) main.post(() -> proactiveBusy = false);
        });
    }

    /** Fragt den Phasen-Wächter; die Antwort kommt über {@link #watchCallback}. Läuft auf jedem Thread. */
    private boolean sendWatch(String key, String image) {
        return sendWatch(key, image, watchCallback);
    }

    private boolean sendWatch(String key, String image, ClaudeClient.Callback callback) {
        JSONArray messages = new JSONArray();
        try {
            JSONArray content = new JSONArray()
                    .put(new JSONObject().put("type", "image").put("source", new JSONObject()
                            .put("type", "base64").put("media_type", "image/jpeg").put("data", image)))
                    .put(new JSONObject().put("type", "text").put("text", ProactiveCoach.WATCH_PROMPT));
            messages.put(new JSONObject().put("role", "user").put("content", content));
        } catch (JSONException e) {
            return false;
        }
        // Günstiges Modell: Der Wächter sagt nur „PLANUNG 3-2“, „KAMPF“ oder „ANDERES“.
        claude.sendQuick(key, Prefs.BUDGET_MODEL, ProactiveCoach.WATCH_SYSTEM, messages, callback);
        return true;
    }

    /**
     * CI-Gerätetest mit Arts echten TFT-Bildern (.github/testbilder/waechter, Spielernamen unkenntlich). Das Skript
     * kopiert sie in den App-Ordner „waechter“; jedes geht nacheinander an den Wächter, genau wie im Spiel. Die Bilder
     * haben schon die Größe, die die App schickt (lange Kante 1280, JPEG 75). Protokoll je Bild
     * „waechter-bild <Datei>: <Antwort>“, zum Schluss „waechter-bilder fertig“.
     */
    /**
     * Machbarkeit der Offline-Stimme (Pocket TTS über sherpa-onnx): Das CI-Skript legt Modell und Sprachprobe in den
     * App-Ordner „pocket“; hier wird geladen, drei Sätze erzeugt und gespielt, Zeiten protokolliert
     * („pocket: …“) und der erste Satz als „pocket/probe.wav“ gespeichert, damit Art ihn anhören kann.
     */
    private void testPocket() {
        final java.io.File dir = new java.io.File(getFilesDir(), "pocket");
        new Thread(() -> {
            if (!PocketVoice.present(dir)) {
                testLog("pocket fehler: Modell fehlt in " + dir);
                return;
            }
            PocketVoice voice = null;
            try {
                long t0 = SystemClock.elapsedRealtime();
                voice = new PocketVoice(dir, new java.io.File(dir, "stimme.wav"), 2);
                testLog("pocket: geladen in " + (SystemClock.elapsedRealtime() - t0) + " ms, "
                        + voice.sampleRate() + " Hz, " + Runtime.getRuntime().availableProcessors() + " Kerne");
                // Nur ein Satz: Im Emulator braucht jeder ~45 s, und ein zweites Modell im Speicher (der folgende
                // Test „als Jarvis' Stimme“) würde dort den Prozess beenden. Drei Sätze misst Arts Handy.
                String[] saetze = {"Zu Diensten, Sir."};
                for (int i = 0; i < saetze.length; i++) {
                    long t = SystemClock.elapsedRealtime();
                    com.k2fsa.sherpa.onnx.GeneratedAudio audio = voice.generate(saetze[i]);
                    long ms = SystemClock.elapsedRealtime() - t;
                    float sec = audio.getSamples().length / (float) audio.getSampleRate();
                    testLog(String.format(java.util.Locale.GERMANY, "pocket satz %d: %d ms für %.2f s Audio (RTF %.2f)",
                            i + 1, ms, sec, sec > 0 ? ms / 1000f / sec : 0));
                    if (i == 0) audio.save(new java.io.File(dir, "probe.wav").getAbsolutePath());
                    try {
                        PcmPlayer.play(audio.getSamples(), audio.getSampleRate());
                    } catch (RuntimeException e) { // Emulator ohne Ton: Abspielen ist hier Beiwerk
                        testLog("pocket abspielen: " + e.getMessage());
                    }
                }
                testLog("pocket fertig");
            } catch (Throwable e) { // auch UnsatisfiedLinkError – genau das soll die Machbarkeit zeigen
                testLog("pocket fehler: " + e);
            } finally {
                if (voice != null) voice.release();
            }
        }, "pocket-test").start();
    }

    private void testWatchFiles() {
        java.io.File[] files = new java.io.File(getFilesDir(), "waechter").listFiles((d, n) -> n.endsWith(".jpg"));
        if (files == null || files.length == 0) {
            testLog("waechter-bilder fertig (keine Bilder gefunden)");
            return;
        }
        java.util.Arrays.sort(files);
        testWatchFile(files, 0);
    }

    private void testWatchFile(java.io.File[] files, int i) {
        if (i >= files.length) {
            testLog("waechter-bilder fertig");
            return;
        }
        final java.io.File f = files[i];
        String image;
        try {
            image = android.util.Base64.encodeToString(java.nio.file.Files.readAllBytes(f.toPath()),
                    android.util.Base64.NO_WRAP);
        } catch (java.io.IOException e) {
            testLog("waechter-bild " + f.getName() + ": fehler " + e.getMessage());
            testWatchFile(files, i + 1);
            return;
        }
        sendWatch(prefs.apiKey(), image, new ClaudeClient.Callback() {
            @Override
            public void onResult(String text) {
                // Rohantwort und was Jarvis daraus macht – der Gerätetest prüft die Entscheidung („=> …“).
                testLog("waechter-bild " + f.getName() + ": " + text.trim().replace('\n', ' ')
                        + " => " + ProactiveCoach.label(ProactiveCoach.parse(text)));
                testWatchFile(files, i + 1);
            }

            @Override
            public void onError(String message) {
                testLog("waechter-bild " + f.getName() + ": fehler " + message);
                testWatchFile(files, i + 1);
            }
        });
    }

    /**
     * CI-Gerätetest: Der Emulator hat keine Bildschirmfreigabe. Ein gemalter Einstellungs-Bildschirm (kein TFT)
     * geht an den Wächter – richtig ist „ANDERES“. Prüft Modellname, Bildformat und Antwortformat gegen die echte API.
     */
    private void testWatch() {
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1280, 590, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);
        c.drawColor(0xFFF2F2F2);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF202020);
        p.setTextSize(56);
        c.drawText("Einstellungen", 60, 110, p);
        p.setTextSize(40);
        String[] rows = {"WLAN", "Bluetooth", "Akku", "Anzeige", "Töne und Vibration"};
        for (int i = 0; i < rows.length; i++) c.drawText(rows[i], 60, 200 + i * 80, p);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out);
        bmp.recycle();
        testLog("waechter-test gesendet");
        sendWatch(prefs.apiKey(), android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP));
    }

    private final ClaudeClient.Callback watchCallback = new ClaudeClient.Callback() {
        @Override
        public void onResult(String text) {
            proactiveBusy = false;
            ProactiveCoach.Phase phase = ProactiveCoach.parse(text);
            testLog("phase: " + text.trim().replace('\n', ' '));
            long now = SystemClock.elapsedRealtime();
            if (coachPolicy.due(phase, now) && coachNow(phase)) coachPolicy.coached(phase, now);
        }

        @Override
        public void onError(String message) {
            proactiveBusy = false; // nächster Blick in ein paar Sekunden
            testLog("phase fehler: " + message);
        }
    };

    /** Neue Planungsphase: Sonnet schaut aufs scharfe Bild und sagt von selbst den wichtigsten Befehl an. */
    private boolean coachNow(ProactiveCoach.Phase phase) {
        if (!running || grabber == null || !quietForCoach()) return false;
        if (state == State.LISTENING) {
            // Dauergespräch hört gerade (noch ohne Worte) zu – kurz unterbrechen, danach geht es weiter.
            main.removeCallbacks(pauseElapsed);
            releaseRecognizer();
            setState(State.IDLE);
        }
        String key = prefs.apiKey();
        if (key.isEmpty()) return false;
        long now = System.currentTimeMillis();
        if (now - lastInteraction > CONVERSATION_TIMEOUT_MS) history.clear();
        lastInteraction = now;
        final int id = ++requestId;
        timing.reset();
        timing.speechEndedIfUnknown(SystemClock.elapsedRealtime());
        setState(State.THINKING);
        String text = ProactiveCoach.autoMessage(phase);
        // Wie bei einer Frage: im Hintergrund fotografieren, Eingeblendetes kurz verstecken.
        captureThen(id, true, image -> {
            if (image != null) {
                ask(id, key, text, image);
                return;
            }
            setState(State.IDLE);
            scheduleRelisten(RELISTEN_DELAY_MS);
        });
        return true;
    }

    // ---------------------------------------------------------------- Tägliche TFT-Meta

    private boolean metaRunning;
    private long metaLastAttempt;

    /**
     * Holt im Spielmodus einmal am Tag die aktuelle Meta aus dem Internet (im Hintergrund, die Frage wartet nicht).
     * Nach einem Fehlschlag frühestens nach 30 Minuten erneut.
     */
    private void maybeUpdateMeta() {
        if (metaRunning || !prefs.tftMode() || prefs.apiKey().isEmpty()) return;
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
                        // Geprüfte Meta: ohne Set und Patch nicht speichern – nach 30 Minuten neuer Versuch.
                        if (!Meta.isPlausible(text)) {
                            testLog("meta verworfen: " + text);
                            return;
                        }
                        prefs.setMeta(Meta.extract(text), today);
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
                    boolean was = screenShared;
                    screenShared = false;
                    updateProactive();
                    notifyUi();
                    // Android beendet die Freigabe z. B., wenn eine Bildschirmaufnahme startet.
                    if (was && running) {
                        String msg = "Die Bildschirmfreigabe wurde beendet – ich sehe nichts mehr. "
                                + "Tippen Sie oben auf „Bildschirm“, um sie neu zu starten.";
                        statusBriefly(msg);
                        publishExchange(null, msg);
                    }
                });
            }
        }, main);
        Rect b = wm.getCurrentWindowMetrics().getBounds();
        grabber = new ScreenGrabber(projection);
        grabber.start(b.width(), b.height(), getResources().getDisplayMetrics().densityDpi);
        screenShared = true;
        updateProactive();
        notifyUi();
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
        updateTalkAudio(); // vor dem Sprechen: Telefonmodus passend zum Dauergespräch
        if (floatingJ != null) floatingJ.setState(s);
        if (ui != null) ui.onStateChanged(s);
        // Dazwischenreden: Spricht Jarvis im Dauergespräch, lauscht ein eigenes Mikrofon mit
        // Echo-Unterdrückung, ob Art anfängt zu reden – und hört dabei auf „Jarvis“.
        if (s == State.SPEAKING && old != State.SPEAKING) {
            // Die Echo-Schranke lernt erst, wenn die Stimme wirklich zu hören ist (siehe onVoiceStarted).
            bargeIn.newUtterance();
            if (bargeInWanted()) {
                bargeIn.setWakeThreshold(Prefs.thresholdFor(prefs.sensitivity()));
                bargeIn.setMargin(SpeechGate.marginFor(prefs.bargeInSensitivity()));
                bargeIn.start();
            }
        } else if (s != State.SPEAKING) {
            bargeIn.stop(null);
        }
    }

    private boolean bargeInWanted() {
        return continuous && prefs.bargeIn() && micGranted();
    }

    /** Art redet dazwischen: Stimme sofort aus, zuhören. */
    private void onArtSpeaks() {
        if (!running || !continuous || state != State.SPEAKING) return;
        stopSpeaking();
        startListening(true);
    }

    private void notifyUi() {
        if (ui != null) ui.onStatusChanged();
    }

    private void publishExchange(String you, String jarvis) {
        if (you != null) lastYou = you;
        if (jarvis != null) lastJarvis = jarvis;
        // ("", "") = neues Gespräch → Verlauf leeren. Sonst mitschreiben.
        if (you != null && you.isEmpty() && jarvis != null && jarvis.isEmpty()) chatLog.clear();
        if (you != null && !you.isEmpty()) chatLog.art(you);
        if (jarvis != null && !jarvis.isEmpty()) chatLog.jarvis(jarvis);
        if (ui != null) ui.onExchange(lastYou, lastJarvis);
    }

    // ---------------------------------------------------------------- Ablauf

    /** Antippen (Reaktor, J, Benachrichtigung, Seitentaste). */
    private void onTap() {
        // Läuft das Dauergespräch, beendet Antippen es – egal in welchem Zustand.
        if (continuous) {
            stopContinuous(true);
            return;
        }
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
                startContinuous();
                break;
            default:
                startContinuous();
        }
    }

    private void resetConversation() {
        history.clear();
        requestId++;
        // Neues Gespräch: ElevenLabs darf wieder ran (Wunsch #62). Innerhalb eines Gesprächs bleibt die Stimme gleich.
        elevenFailed = false;
        elevenWarned = false;
        // Auch der Agent fängt neu an, sonst zieht er den alten Zusammenhang mit.
        agentJobId++;
        if (prefs.agentReady()) agent.neueSitzung(prefs.agentPort(), prefs.agentToken());
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
        startListening(false);
    }

    /** @param quiet ohne Signalton – im Dauergespräch soll nicht nach jeder Antwort ein Ton kommen. */
    private void startListening(boolean quiet) {
        if (!micGranted()) {
            say("Mir fehlt die Mikrofon-Berechtigung. Bitte öffnen Sie die Jarvis-App.");
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
        if (prefs.earcon() && !quiet) earcon.play();
        if (prefs.tftMode()) subtitles.hide(); // alte Antwort weg, Spielbild frei
        status("Ich höre …");
        // Erst das Dazwischenreden-Mikrofon freigeben, dann übernimmt die Spracherkennung.
        bargeIn.stop(() -> {
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
                // Verbindung zu Anthropic aufbauen, während Art noch spricht (kostenlos, siehe ClaudeClient.warmUp).
                if (!prefs.apiKey().isEmpty()) ClaudeClient.warmUp();
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
                if (continuous) {
                    // Dauergespräch: Stille ist normal – einfach weiter zuhören. Echte Fehler (Erkenner
                    // belegt, Netz weg) mit wachsender Pause wiederholen; mit „Immer zuhören“ danach alle 30 s
                    // weiter versuchen statt aufzugeben (ListenRules).
                    boolean silence = error == SpeechRecognizer.ERROR_NO_MATCH
                            || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                    if (silence) {
                        continuousErrors = 0;
                        scheduleRelisten(150);
                    } else {
                        retryListening("Die Spracherkennung meldet immer wieder Fehler " + error + ".");
                    }
                    return;
                }
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    // Nach „Jarvis“ ohne Frage einfach still zurück in Bereitschaft.
                    statusBriefly(heardSomething ? "Ich habe Sie nicht verstanden." : "Ich habe nichts gehört.");
                } else {
                    String msg = "Die Spracherkennung meldet Fehler " + error + ".";
                    if (prefs.tftMode()) say(msg);
                    else statusBriefly(msg + " Versuchen Sie es einfach noch einmal.");
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
                // Im Dauergespräch nicht jedes Mal ansagen – sonst Endlosschleife aus Ansage und Neustart.
                if (continuous) retryListening("Die Spracherkennung konnte nicht starten.");
                else say("Die Spracherkennung konnte nicht starten.");
            }
        }
    }

    /**
     * Echter Fehler im Dauergespräch: mit wachsender Pause neu versuchen. Mit „Immer zuhören“ nie aufgeben, sondern
     * nach fünf Fehlern alle 30 s weiter – einmal still als Hinweis eingeblendet. Ohne „Immer zuhören“ wie bisher:
     * nach fünf Fehlern Dauergespräch beenden und es sagen.
     */
    private void retryListening(String problem) {
        long delay = ListenRules.retryDelay(++continuousErrors, prefs.alwaysListen());
        if (delay < 0) {
            stopContinuous(false);
            say(problem + " Ich beende das Dauergespräch.");
            return;
        }
        testLog("erkenner fehler " + continuousErrors + ", neuer versuch in " + delay + " ms");
        if (continuousErrors == ListenRules.QUICK_RETRIES + 1) {
            statusBriefly(problem + " Ich versuche es alle 30 Sekunden weiter.");
        }
        scheduleRelisten(delay);
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
            if (continuous) scheduleRelisten(150);
            else statusBriefly("Ich habe nichts verstanden.");
            return;
        }
        if (continuous) {
            continuousLastSpeech = SystemClock.elapsedRealtime();
            continuousErrors = 0;
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
        final boolean testOpen = testOpenMic;
        testOpenMic = false;
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
        // „Neue Partie“: Verlauf der letzten Partie vergessen (und Spielmodus an, falls noch aus).
        if (Commands.newGame(text)) {
            history.clear();
            boolean wasGame = prefs.tftMode();
            if (!wasGame) {
                prefs.setTftMode(true);
                Backup.autoSave(this, prefs);
            }
            setState(State.IDLE);
            applySettings();
            speak(wasGame ? "Neue Partie. Viel Erfolg, Sir." : "Neue Partie, Spielmodus aktiv. Viel Erfolg, Sir.");
            return;
        }
        // „Gesprächsmodus an“, „Hör durchgehend zu“ / „Gespräch beenden“ – Dauergespräch an oder aus.
        Boolean talk = Commands.conversationMode(text);
        if (talk == null && continuous && Commands.endsConversation(text)) talk = Boolean.FALSE;
        if (talk != null) {
            if (talk) {
                setState(State.IDLE);
                if (continuous) {
                    speak("Ich höre schon durchgehend zu, Sir.");
                } else {
                    continuous = true;
                    listenPaused = false;
                    continuousLastSpeech = SystemClock.elapsedRealtime();
                    continuousErrors = 0;
                    notifyUi();
                    speak("Dauergespräch an. Ich höre ab jetzt durchgehend zu.");
                }
            } else if (continuous) {
                stopContinuous(true);
            } else {
                setState(State.IDLE);
                speak("Das Dauergespräch ist gerade nicht an.");
            }
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
        // „Notier für die Entwicklung: …“ – Wunsch direkt als Aufgabe ins Repo, ohne Umweg über Claude.
        String wish = Wishes.command(text);
        if (wish != null) {
            noteWish(Wishes.titleFrom(wish), wish, true);
            return;
        }
        // Lange Aufgaben gibt Art ausdrücklich ab („Übernimm das: …“). Die macht der
        // Agent in Termux über Claude Code – das zählt gegen das Abo, nicht gegen das
        // API-Guthaben. Im Spielmodus nie: der Agent braucht Sekunden bis Minuten.
        if (prefs.agentReady() && !prefs.tftMode()) {
            String auftrag = Commands.agentTask(text);
            if (auftrag != null) {
                beauftrageAgenten(auftrag);
                return;
            }
        }
        String key = prefs.apiKey();
        if (key.isEmpty()) {
            setState(State.IDLE);
            say("Mir fehlt noch der API-Schlüssel. Bitte tragen Sie ihn in den Jarvis-Einstellungen ein.");
            return;
        }
        final int id = ++requestId;
        setState(State.THINKING);
        status("„" + text + "“");
        // Kostenbremse bei offenem Mikrofon: Nach einer Gesprächspause fragt erst der günstige Filter (Haiku, nur
        // Text), ob der Satz Jarvis galt. Bildschirmfoto und Filter laufen gleichzeitig – die Frage wartet nur auf
        // den langsameren der beiden.
        long nowRt = SystemClock.elapsedRealtime();
        boolean openMic = continuous || testOpen;
        final PendingAsk pending = new PendingAsk(id, key, text);
        boolean gateNeeded = ListenRules.needsGate(openMic, nowRt, lastExchangeAt, text);
        if (gateNeeded && prefs.overCostLimit(today())) {
            // Über der Tagesgrenze: nicht beim Namen genannt und kein laufendes Gespräch – schweigen, ohne Filter,
            // ohne Bild, ohne Sonnet. Mit „Jarvis“ im Satz oder als Folgefrage geht es weiter normal.
            testLog("antwort [kostengrenze]: –");
            setState(State.IDLE);
            scheduleRelisten(RELISTEN_DELAY_MS);
            return;
        }
        if (gateNeeded) {
            askGate(key, text, nowRt, pending);
        } else {
            pending.gateDone = true;
        }
        captureThen(id, prefs.tftMode(), pending::imageReady);
    }

    /** Wartet auf Bildschirmfoto und (falls nötig) Filter; dann geht die Frage an Claude – oder Jarvis schweigt. */
    private final class PendingAsk {
        final int id;
        final String key;
        final String text;
        final long started = SystemClock.elapsedRealtime();
        boolean imageDone;
        boolean gateDone;
        boolean blocked;
        String image;

        PendingAsk(int id, String key, String text) {
            this.id = id;
            this.key = key;
            this.text = text;
        }

        void imageReady(String img) {
            image = img;
            imageDone = true;
            go();
        }

        void gateReady(boolean addressed) {
            blocked = !addressed;
            gateDone = true;
            go();
        }

        private void go() {
            if (!imageDone || !gateDone || id != requestId || !running) return;
            if (blocked) {
                // Galt nicht Jarvis (Fernseher, Gespräch mit anderen): schweigen und weiter zuhören, ohne Sonnet.
                testLog("antwort [gefiltert]: –");
                setState(State.IDLE);
                scheduleRelisten(RELISTEN_DELAY_MS);
                return;
            }
            askClaude(id, key, text, image);
        }
    }

    /** Fragt den Filter (Haiku): Galt der Satz Jarvis? Fehler oder unklare Antwort → ja, Jarvis antwortet. */
    private void askGate(String key, String text, long nowRt, PendingAsk pending) {
        long since = lastExchangeAt == 0 ? -1 : (nowRt - lastExchangeAt) / 1000;
        String prompt = ListenRules.gatePrompt(text, since < 0 ? null : lastJarvis, Math.max(0, since), prefs.tftMode());
        JSONArray messages = new JSONArray();
        try {
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
        } catch (JSONException e) {
            pending.gateDone = true;
            return;
        }
        final long t0 = SystemClock.elapsedRealtime();
        claude.sendGate(key, Prefs.BUDGET_MODEL, ListenRules.GATE_SYSTEM, messages, new ClaudeClient.Callback() {
            @Override
            public void onResult(String reply) {
                boolean addressed = ListenRules.addressed(reply);
                testLog("filter: " + (addressed ? "JA" : "NEIN") + " (" + (SystemClock.elapsedRealtime() - t0) + " ms)");
                pending.gateReady(addressed);
            }

            @Override
            public void onError(String message) {
                testLog("filter fehler: " + message);
                pending.gateReady(true);
            }
        });
    }

    /**
     * Bildschirmfoto im Hintergrund, Ergebnis auf dem Haupt-Thread (null ohne Bildschirmfreigabe). Ist etwas
     * eingeblendet, wird es kurz versteckt, damit es nicht auf dem Foto landet.
     */
    private void captureThen(int id, boolean game, java.util.function.Consumer<String> done) {
        final ScreenGrabber g = grabber;
        if (g == null) {
            done.accept(null);
            return;
        }
        boolean overlayVisible = floatingJ != null || subtitles.isShowing();
        if (overlayVisible) setCaptureHidden(true);
        Runnable shoot = () -> captureExecutor.execute(() -> {
            String image = g.captureJpegBase64(game);
            main.post(() -> {
                if (overlayVisible) setCaptureHidden(false);
                if (id == requestId && running) done.accept(image);
            });
        });
        // 120 ms reichen, damit das Bild ohne Einblendung neu gezeichnet ist; sonst sofort.
        if (overlayVisible) main.postDelayed(shoot, 120);
        else shoot.run();
    }

    /** Frage an Claude; ein Gespräch, das eine Viertelstunde ruhte, beginnt frisch. */
    private void askClaude(int id, String key, String text, String image) {
        long now = System.currentTimeMillis();
        if (now - lastInteraction > CONVERSATION_TIMEOUT_MS) history.clear();
        lastInteraction = now;
        ask(id, key, text, image);
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
            // Uhrzeit und Bildschirm-Hinweis stehen vor der Frage statt in der Grundanweisung – so bleibt
            // die Grundanweisung gleich und kommt aus dem Zwischenspeicher (siehe ClaudeClient.body).
            content.put(new JSONObject().put("type", "text")
                    .put("text", Persona.turnContext(prefs.tftMode(), image != null,
                            continuous && !text.startsWith(ProactiveCoach.AUTO_PREFIX), new java.util.Date())));
            content.put(new JSONObject().put("type", "text").put("text", text));
            messages.put(new JSONObject().put("role", "user").put("content", content));
        } catch (JSONException e) {
            setState(State.IDLE);
            say("Interner Fehler beim Vorbereiten der Anfrage.");
            return;
        }

        // Werkzeuge (Websuche, Kalender) nur im Alltag mit Claude – der Spielmodus bleibt schnell und nutzt die Tages-Meta.
        boolean withTools = !prefs.tftMode();
        String system = Persona.system(prefs.tftMode(), prefs.memory(), prefs.metaText(), prefs.metaDate(),
                withTools, prefs.agentReady());
        // Satzweise sprechen, während Claude noch schreibt (Handy-Stimme). ElevenLabs bekommt die Antwort weiter am
        // Stück – dort wäre jeder Satz eine eigene Anfrage.
        final StreamSpeech stream = !prefs.elevenKey().isEmpty() && !pocketWanted() ? null : new StreamSpeech(id);
        ClaudeClient.Callback callback = new ClaudeClient.Callback() {
            @Override
            public void onResult(String answer) {
                timing.firstAnswer(SystemClock.elapsedRealtime());
                if (id != requestId || !running) return; // überholt (neue Frage, neues Gespräch)
                // Art hat in eigenen Worten um Dauerzuhören gebeten (oder es beendet) – Claude meldet das per Steuerzeile.
                Boolean dauer = Commands.continuousTag(answer);
                answer = Commands.stripControlTags(answer);
                // Spielmodus: die stille Ablese-Zeile [STAND: …] – nie vorlesen, aber im Verlauf behalten.
                String stand = GameReadout.find(answer);
                answer = GameReadout.strip(answer);
                if (stand != null) testLog("stand: " + stand);
                // [STILL]: ungefragte Ansage ohne Wichtiges – oder beim Dauerzuhören Gerede, das nicht Jarvis galt
                // (Fernseher, Gespräch mit anderen). Schweigen und weiter zuhören.
                if (ProactiveCoach.isStill(answer)) {
                    testLog("still");
                    setState(State.IDLE);
                    scheduleRelisten(RELISTEN_DELAY_MS);
                    return;
                }
                if (dauer != null) setContinuousFromAnswer(dauer);
                // Eigene Frage in der Planungsphase: Die ungefragte Ansage dieser Runde entfällt.
                if (prefs.tftMode() && !text.startsWith(ProactiveCoach.AUTO_PREFIX)) {
                    coachPolicy.userAsked(SystemClock.elapsedRealtime());
                }
                // Wünsche, die Art in eigenen Worten geäußert hat – Claude meldet sie per Steuerzeile.
                java.util.List<String[]> wishes = Wishes.tags(answer);
                answer = Wishes.stripTags(answer);
                for (String[] w : wishes) noteWish(w[0], w[1], false);
                Memory.Outcome tagged = Memory.tags(answer, prefs.memory());
                // Nur bei echter Änderung speichern: Die Sicherungsdatei wurde sonst nach JEDER Antwort neu
                // geschrieben – auf dem Haupt-Thread, direkt bevor Jarvis zu sprechen beginnt.
                if (!tagged.memory.equals(prefs.memory())) {
                    prefs.setMemory(tagged.memory);
                    Backup.autoSave(JarvisService.this, prefs);
                }
                answer = Text.replyOrFallback(answer, Text.cleanForSpeech(tagged.reply));
                ClaudeClient.Usage used = ClaudeClient.lastUsage;
                testLog("antwort [" + (used == null ? "?" : used.summary()) + costSuffix() + netSuffix() + "]: " + answer);
                remember("user", image != null ? "[Bildschirmfoto war angehängt] " + text : text);
                remember("assistant", GameReadout.forHistory(stand, answer));
                finishStream(stream, answer);
            }

            @Override
            public void onError(String message) {
                if (id != requestId || !running) return;
                testLog("fehler: " + message);
                // Hat Art die gestreamte Antwort schon unterbrochen, nicht in sein Zuhören hineinreden.
                if (stream != null && stream.interrupted(speechGen)) return;
                if (text.startsWith(ProactiveCoach.AUTO_PREFIX)) {
                    // Ungefragte Ansage fehlgeschlagen (Netz, Überlast): nicht mitten ins Spiel hineinmelden.
                    setState(State.IDLE);
                    scheduleRelisten(RELISTEN_DELAY_MS);
                    return;
                }
                setState(State.IDLE);
                say(message);
            }
        };
        timing.sent(SystemClock.elapsedRealtime());
        lastModel = prefs.model();
        // Nachdenken nach Bedarf: Bei einfachen Fragen antwortet Claude sofort, bei kniffligen denkt er kurz nach
        // (so wie der allererste Jarvis). Im Spielmodus etwas gründlicher und ohne Werkzeuge; im Alltag mit Werkzeugen.
        // Spielmodus standardmäßig „low“ wie im Alltag (Wunsch #23: Tempo zuerst) – das genaue Hinsehen übernimmt die
        // Ablese-Zeile. „Gründlicher nachdenken“ in den Einstellungen holt „medium“ zurück: laut Claude-Doku denkt das
        // Modell ab „medium“ vor fast jeder Antwort nach, bei „low“ nur bei kniffligen Fragen.
        claude.send(key, prefs.model(), system, messages, effortFor(text),
                withTools ? tools : null, stream == null ? null : delta -> onStreamText(stream, delta), callback);
    }

    /**
     * Nachdenk-Stufe: Arts Fragen „low“ (Tempo), mit „Gründlicher nachdenken“ „medium“. Ungefragte Ansagen des
     * proaktiven Coachings immer „medium“ – darauf wartet niemand, und Art ist die Qualität wichtiger (Gedächtnis).
     */
    private String effortFor(String text) {
        if (!prefs.tftMode()) return "low";
        if (text.startsWith(ProactiveCoach.AUTO_PREFIX) || prefs.gameThinkDeep()) return "medium";
        return "low";
    }

    // ---------------------------------------------------------------- Streaming: satzweise sprechen

    /** Eine gestreamte Antwort: Fertige Sätze gehen an die Sprachausgabe, während Claude noch schreibt. */
    private static final class StreamSpeech {
        final int id;
        final SpeechChunker chunker = new SpeechChunker();
        final StringBuilder said = new StringBuilder();
        /** Sprechvorgang dieser Antwort; -1 = es wurde noch nichts gesagt. */
        int gen = -1;
        boolean muted;

        StreamSpeech(int id) {
            this.id = id;
        }

        /** Wurde die schon begonnene Antwort abgebrochen (Dazwischenreden, Antippen, andere Ansage)? */
        boolean interrupted(int currentGen) {
            return muted || (gen != -1 && gen != currentGen);
        }
    }

    private void onStreamText(StreamSpeech s, String delta) {
        if (s.id != requestId || !running || s.muted) return;
        for (String sentence : s.chunker.feed(delta)) sayStreamed(s, sentence);
    }

    private void sayStreamed(StreamSpeech s, String sentence) {
        if (s.gen == -1) {
            timing.firstAnswer(SystemClock.elapsedRealtime()); // „KI“ = Wartezeit bis zum ersten Satz
            s.gen = newSpeech(true);
        } else if (s.gen != speechGen) {
            s.muted = true; // abgebrochen – der Rest wird nicht mehr gesprochen
            return;
        }
        boolean first = s.said.length() == 0;
        if (!first) s.said.append(' ');
        s.said.append(sentence);
        testLog("satz: " + sentence);
        caption(s.said.toString());
        publishExchange(null, s.said.toString());
        speakDevice(sentence, s.gen, first);
    }

    /** Antwort komplett: den Rest sprechen – oder alles am Stück, wenn bisher kein Satz fertig war. */
    private void finishStream(StreamSpeech s, String answer) {
        if (s == null || s.gen == -1) {
            speak(answer);
            return;
        }
        if (s.interrupted(speechGen)) return; // abgebrochen: Verlauf ist gespeichert, gesprochen wird nichts mehr
        String rest = s.chunker.finish();
        if (!rest.isEmpty()) sayStreamed(s, rest);
        moreComing = false;
        speechDone(s.gen);
    }

    /**
     * Gibt einen Auftrag an den Agenten in Termux.
     *
     * Eigener Zähler statt {@link #requestId}: Ein Auftrag kann Minuten dauern, und
     * in der Zeit darf Art ruhig etwas anderes fragen – die Antwort des Agenten geht
     * dadurch nicht verloren. Nur ein neuerer Auftrag überholt einen älteren.
     */
    private void beauftrageAgenten(String auftrag) {
        final int job = ++agentJobId;
        setState(State.THINKING);
        status("Auftrag an den Agenten: „" + auftrag + "“");
        agent.auftrag(prefs.agentPort(), prefs.agentToken(), auftrag, false, reply -> {
            if (job != agentJobId || !running) return;
            main.removeCallbacks(agentPoll); // Auftrag fertig: keine Zwischenansagen mehr
            agentPollFor = -1;
            if (state == State.LISTENING && utterance.length() == 0) {
                // Dauergespräch hört gerade (noch ohne Worte) zu – kurz unterbrechen, damit die
                // Antwort nicht von der Spracherkennung mitgehört wird. Danach geht es weiter.
                main.removeCallbacks(pauseElapsed);
                releaseRecognizer();
                setState(State.IDLE);
            }
            if (reply.ok()) {
                remember("user", "[Auftrag an den Agenten] " + auftrag);
                remember("assistant", reply.antwort);
                speak(reply.antwort);
            } else {
                setState(State.IDLE);
                say(reply.fehler);
            }
        });
        // Sofort eine kurze Rückmeldung, damit Art nicht minutenlang ins Leere wartet.
        speak("Ich kümmere mich darum, Sir.");
        // Danach alle AgentProgress.ABSTAND_MS einen Zwischenstand ansagen, solange der Auftrag läuft.
        agentPollFor = job;
        agentAnsageZeit = SystemClock.elapsedRealtime();
        main.postDelayed(agentPoll, AgentProgress.ABSTAND_MS);
    }

    /** Fragt den Zwischenstand ab und sagt ihn an, wenn fällig und Jarvis gerade ruhig ist; plant sich neu. */
    private final Runnable agentPoll = new Runnable() {
        @Override
        public void run() {
            final int job = agentPollFor;
            if (!running || job < 0 || job != agentJobId) return; // Auftrag vorbei oder neuer gestartet
            agent.fortschritt(prefs.agentPort(), prefs.agentToken(), (beschaeftigt, text) -> {
                if (!running || job != agentJobId) return;
                if (beschaeftigt && ruhigFuerAgentAnsage()
                        && AgentProgress.faellig(SystemClock.elapsedRealtime(), agentAnsageZeit)) {
                    agentAnsageZeit = SystemClock.elapsedRealtime();
                    speak(AgentProgress.ansage(text));
                }
            });
            main.postDelayed(this, AgentProgress.ABSTAND_MS);
        }
    };

    /** Nur ansagen, wenn Jarvis nicht gerade selbst spricht und Art nicht mitten im Satz ist. */
    private boolean ruhigFuerAgentAnsage() {
        return state != State.SPEAKING && !(state == State.LISTENING && (utterance.length() > 0 || heardSomething));
    }

    // ---------------------------------------------------------------- Dauergespräch

    /**
     * Dauergespräch mit Claude: Einmal antippen, dann hört Jarvis nach jeder Antwort von
     * selbst weiter zu – bis Art erneut antippt, „Gespräch beenden“ sagt oder 15 Minuten
     * lang nichts sagt. Ist der Bildschirm geteilt, bekommt jede Frage ein frisches Bild.
     */
    private void startContinuous() {
        continuous = true;
        listenPaused = false;
        continuousLastSpeech = SystemClock.elapsedRealtime();
        lastExchangeAt = continuousLastSpeech; // Art hat gerade angetippt – seine Frage gilt Jarvis
        continuousErrors = 0;
        notifyUi();
        startListening();
    }

    /** @param announce kurz ansagen, dass es vorbei ist (bei Antippen und Sprachbefehl). */
    private void stopContinuous(boolean announce) {
        if (!continuous) return;
        continuous = false;
        main.removeCallbacks(relisten);
        if (state == State.THINKING) requestId++; // Art hat beendet – die ausstehende Antwort nicht mehr vorlesen
        stopSpeaking();
        if (state == State.LISTENING) {
            main.removeCallbacks(pauseElapsed);
            releaseRecognizer();
            utterance.setLength(0);
        }
        setState(State.IDLE);
        notifyUi();
        if (!announce) return;
        // Von Art beendet (Antippen, „Gespräch beenden“): Mit „Immer zuhören“ pausiert, bis er wieder antippt.
        if (prefs.alwaysListen()) {
            listenPaused = true;
            speak("Ich höre nicht mehr zu. Tippen Sie, wenn Sie mich brauchen.");
        } else {
            speak("Dauergespräch beendet.");
        }
    }

    /** Nach einer Antwort (oder nach Stille) wieder zuhören, solange das Dauergespräch läuft. */
    private void scheduleRelisten(long delayMs) {
        main.removeCallbacks(relisten);
        if (continuous && running) main.postDelayed(relisten, delayMs);
    }

    /** Claude hat das Dauergespräch per Steuerzeile ein- oder ausgeschaltet. Die Antwort spricht er selbst. */
    private void setContinuousFromAnswer(boolean on) {
        if (on == continuous) return;
        continuous = on;
        continuousLastSpeech = SystemClock.elapsedRealtime();
        continuousErrors = 0;
        if (!on) main.removeCallbacks(relisten);
        notifyUi();
    }

    private void relistenIfContinuous() {
        if (!continuous || !running || state != State.IDLE) return;
        // Während TFT vorne ist, keine Viertelstunden-Grenze – eine Partie hat lange stille Phasen. Mit „Immer zuhören“
        // auch nicht: Dann endet das Zuhören mit dem Bildschirm, nicht nach einer Viertelstunde.
        if (!gameWatch.inGame() && !prefs.alwaysListen()
                && SystemClock.elapsedRealtime() - continuousLastSpeech > CONTINUOUS_IDLE_MS) {
            stopContinuous(false);
            speak("Ich habe eine Viertelstunde nichts gehört und höre nicht mehr durchgehend zu. Tippen Sie, wenn Sie mich brauchen.");
            return;
        }
        startListening(true);
    }

    // ---------------------------------------------------------------- Wünsche an die Entwicklung

    /**
     * Notiert einen Wunsch und legt ihn als GitHub-Issue an, wenn ein Schlüssel da ist.
     * @param confirm selbst bestätigen (beim Sprachbefehl); bei der Steuerzeile bestätigt Claude schon.
     */
    private void noteWish(String title, String body, boolean confirm) {
        // Schon notiert (gleicher Titel oder dasselbe Thema)? Dann kein zweites Issue. Vorher wurde jeder Wunsch
        // gesendet, auch wenn derselbe Titel schon gespeichert war – am 8. Nov. neun Issues für drei Wünsche.
        Wishes.Wish known = Wishes.similar(prefs.wishes(), title, body);
        if (known == null) {
            prefs.setWishes(Wishes.add(prefs.wishes(), title, body));
            Backup.autoSave(this, prefs);
        }
        String token = prefs.githubToken();
        if (confirm) {
            setState(State.IDLE);
            if (known != null) {
                speak(known.open() ? "Das habe ich schon notiert, Sir."
                        : "Das liegt schon als Aufgabe " + known.status + " bei der Entwicklung, Sir.");
            } else {
                speak(token.isEmpty()
                        ? "Notiert, Sir. Damit es direkt als Aufgabe an die Entwicklung geht, fehlt noch der GitHub-Schlüssel in den Einstellungen."
                        : "Notiert, Sir. Ich gebe es als Aufgabe an die Entwicklung weiter.");
            }
        }
        if (token.isEmpty() || known != null) return;
        wishSender.send(token, title, body, today(), (issue, error) -> {
            if (issue > 0) {
                prefs.setWishes(Wishes.markSent(prefs.wishes(), title, issue));
                Backup.autoSave(this, prefs);
                publishExchange(null, "Als Aufgabe #" + issue + " an die Entwicklung: " + title);
            } else {
                publishExchange(null, "Wunsch notiert, aber nicht gesendet: " + error);
            }
        });
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

    /** Jeder Sprechvorgang (Antwort, Ansage) bekommt eine Nummer; Rückmeldungen älterer Vorgänge zählen nicht. */
    private int speechGen;
    /** Sätze des laufenden Vorgangs, die die Handy-Stimme noch nicht fertig gesprochen hat. */
    private int queuedUtterances;
    /** Die Antwort streamt noch – nach dem letzten Satz kann noch einer kommen. */
    private boolean moreComing;
    private int utteranceSeq;
    /** Vorgang, zu dem {@link #pendingSpeech} gehört. */
    private int pendingGen;

    /** Beginnt einen neuen Sprechvorgang; Rückmeldungen des alten zählen ab jetzt nicht mehr. */
    private int newSpeech(boolean streaming) {
        speechGen++;
        queuedUtterances = 0;
        moreComing = streaming;
        pendingSpeech = null;
        return speechGen;
    }

    /** Ein Satz der Handy-Stimme ist durch (oder ging schief). */
    private void utteranceDone(String utteranceId) {
        if (utteranceId == null || !utteranceId.startsWith("jarvis-" + speechGen + "-")) return; // älterer Vorgang
        if (queuedUtterances > 0) queuedUtterances--;
        speechDone(speechGen);
    }

    /**
     * Fertig gesprochen erst, wenn nichts mehr in der Warteschlange ist, die Antwort nicht mehr streamt und kein
     * Text mehr darauf wartet, dass die Sprachausgabe startklar wird (sonst hörte das Dauergespräch schon wieder zu,
     * während Jarvis gleich darauf spricht).
     */
    private void speechDone(int gen) {
        if (gen != speechGen || queuedUtterances > 0 || moreComing) return;
        if (pendingSpeech != null && pendingGen == gen) return; // kommt mit initTts oder spätestens mit dem 6-s-Notausgang
        onSpeechFinished();
    }

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
            if (speechAsCall) tts.setAudioAttributes(TalkAudio.speech(true));
            Voices.apply(tts, prefs, engine);
            appliedVoiceKey = null; // neue Engine → beim nächsten Satz sicher neu einstellen
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { main.post(JarvisService.this::onVoiceStarted); }
                @Override public void onDone(String id) { main.post(() -> utteranceDone(id)); }
                @Override public void onError(String id) { main.post(() -> utteranceDone(id)); }
            });
            if (pendingSpeech != null) {
                String text = pendingSpeech;
                pendingSpeech = null;
                speakNow(text, pendingGen, true);
            }
        });
        tts = holder[0];
    }

    private void say(String text) {
        speak(text);
    }

    /**
     * Tagesgrenze der Kosten erreicht (Einstellungen → Sprachsteuerung). Einmal am Tag still einblenden – nicht
     * sprechen, damit es nicht in eine laufende Antwort oder ins Spiel fährt – und den Phasen-Wächter anhalten.
     * Ab jetzt gehen bei offenem Mikrofon nur noch Sätze mit „Jarvis“ und Folgefragen an Claude (siehe {@link #handleUtterance}).
     */
    private void onCostLimit() {
        String day = today();
        updateProactive();
        if (day.equals(prefs.costLimitNoticeDay())) return;
        prefs.setCostLimitNoticeDay(day);
        testLog("kostengrenze erreicht");
        caption("Tagesgrenze von " + Costs.cents(prefs.costLimitCents() / 100.0)
                + " erreicht – ich antworte nur noch, wenn Sie „Jarvis“ sagen.");
        notifyUi();
    }

    private boolean debuggable() {
        return BuildConfig.DEBUG;
    }

    /** Protokollzeile für den Gesprächstest in der CI; in der Handy-Version (Release) passiert nichts. */
    private void testLog(String line) {
        if (debuggable()) android.util.Log.i(TEST_TAG, line);
    }

    /** Spricht einen ganzen Text am Stück (Ansagen, Antworten ohne Streaming). */
    private void speak(String text) {
        String clean = Text.cleanForSpeech(text);
        testLog("sprechen: " + clean);
        int gen = newSpeech(false);
        if (clean.isEmpty()) {
            onSpeechFinished();
            return;
        }
        caption(clean);
        publishExchange(null, clean);
        // Offline-Stimme, wenn Art sie gewählt hat – sonst Premium-Stimme (ElevenLabs), wenn eingerichtet und in
        // diesem Gespräch noch nicht ausgefallen, sonst die Stimme des Handys (Wunsch #62: kein Hin- und Herspringen).
        switch (VoicePick.choose(pocketWanted(), !prefs.elevenKey().isEmpty(), elevenFailed)) {
            case POCKET:
                speakPocket(clean, gen, true);
                return;
            case PREMIUM:
                speakPremium(clean, gen);
                return;
            default:
                speakDevice(clean, gen, true);
        }
    }

    private void speakPremium(String clean, int gen) {
        setState(State.SPEAKING);
        eleven.speak(prefs.elevenKey(), prefs.elevenVoice(), clean, prefs.rate(), new ElevenTts.Listener() {
            @Override public void onStart() { onVoiceStarted(); }

            @Override
            public void onDone() {
                if (gen == speechGen) onSpeechFinished();
            }

            @Override
            public void onError(String message) {
                if (gen != speechGen) return;
                // Ab jetzt im ganzen Gespräch bei der Handy-Stimme bleiben, statt bei jedem Satz neu ElevenLabs
                // zu versuchen (Wunsch #62). Einmal pro Sitzung Bescheid geben.
                elevenFailed = true;
                if (!elevenWarned) {
                    elevenWarned = true;
                    statusBriefly(message + " Ich spreche mit der Handy-Stimme weiter.");
                }
                speakDevice(clean, gen, true);
            }
        });
    }

    /** Erster Ton einer Antwort: die Zeitmessung abschließen. Die Werte stehen im CI-Gerätetest-Protokoll
     *  (siehe testLog „antwort […]“); auf dem Startbildschirm werden sie nicht mehr gezeigt (Wunsch #71). */
    private void onVoiceStarted() {
        bargeIn.voiceStarted(); // ab jetzt lernt die Echo-Schranke Jarvis' eigene Stimme
        timing.voiceStarted(SystemClock.elapsedRealtime());
    }

    /** „ · Senden 0,4 s (380 KB) · Claude 0,9 s“ – zerlegt die Wartezeit „KI“ (Wunsch #43). */
    private String netSuffix() {
        ClaudeClient.Net net = ClaudeClient.lastNet;
        String n = net == null ? null : net.summary();
        return n == null ? "" : " · " + n;
    }

    /** „ · 0,6 ct“ – Kosten der letzten Antwort in US-Cent; leer bei unbekanntem Modell. */
    private String costSuffix() {
        double usd = ClaudeClient.lastCostUsd;
        return usd < 0 ? "" : " · " + Costs.cents(usd);
    }

    /** Bricht ab, was gerade gesprochen wird – auch den Rest einer noch streamenden Antwort. */
    private void stopSpeaking() {
        speechGen++;
        queuedUtterances = 0;
        moreComing = false;
        pendingSpeech = null;
        if (tts != null) tts.stop();
        eleven.stop();
        if (pocket != null) pocket.stop();
    }

    /** flush: erster Satz eines Vorgangs (verdrängt Älteres); sonst hinten anstellen. */
    private void speakDevice(String clean, int gen, boolean flush) {
        if (gen != speechGen) return;
        if (pocketWanted()) {
            speakPocket(clean, gen, flush);
            return;
        }
        // Wurde in den Einstellungen eine andere Engine gewählt? Dann neu starten und danach sprechen.
        String engineNow = Voices.resolveEngine(this, prefs);
        boolean engineChanged = engineNow == null ? ttsEngine != null : !engineNow.equals(ttsEngine);
        if (engineChanged || !ttsReady) {
            pendingSpeech = pendingSpeech == null ? clean : pendingSpeech + " " + clean;
            pendingGen = gen;
            setState(State.SPEAKING);
            if (engineChanged) initTts(engineNow);
            main.postDelayed(() -> {
                if (pendingSpeech != null && pendingGen == gen) { // Sprachausgabe kam nicht hoch
                    pendingSpeech = null;
                    speechDone(gen);
                }
            }, 6000);
            return;
        }
        speakNow(clean, gen, flush);
    }

    private void speakNow(String clean, int gen, boolean flush) {
        if (gen != speechGen) return;
        int max = TextToSpeech.getMaxSpeechInputLength() - 1;
        String spoken = Text.sirForDeviceVoice(clean); // „Sir“ englisch (Wunsch #46)
        String text = spoken.length() > max ? spoken.substring(0, max) : spoken;
        setState(State.SPEAKING);
        // Stimme nur neu einstellen, wenn sich etwas geändert hat – das Durchsuchen aller Stimmen
        // vor jedem Satz kostete spürbar Zeit.
        String voiceKey = ttsEngine + "|" + prefs.voiceName() + "|" + prefs.rate() + "|" + prefs.pitch();
        if (!voiceKey.equals(appliedVoiceKey)) {
            Voices.apply(tts, prefs, ttsEngine);
            appliedVoiceKey = voiceKey;
        }
        queuedUtterances++;
        int result = tts.speak(text, flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null,
                "jarvis-" + gen + "-" + (++utteranceSeq));
        if (result != TextToSpeech.SUCCESS) {
            // Ohne Rückmeldung der Sprachausgabe bliebe Jarvis sonst für immer im Zustand „spricht“.
            queuedUtterances--;
            speechDone(gen);
        }
    }

    // ---------------------------------------------------------------- Offline-Stimme (Pocket TTS)

    /** Hat Art die Offline-Stimme gewählt, liegt das Modell da, und hat sie bisher nicht versagt? */
    private boolean pocketWanted() {
        if (pocketFailed || !prefs.pocketVoice() || !PocketTrial.downloaded(this)) return false;
        return pocketSpeaker().usable();
    }

    private PocketSpeaker pocketSpeaker() {
        if (pocket == null) {
            pocket = new PocketSpeaker(this, new PocketSpeaker.Listener() {
                @Override
                public void onStart(int gen) {
                    if (gen == speechGen) onVoiceStarted();
                }

                @Override
                public void onDone(int gen) {
                    if (gen != speechGen) return;
                    if (queuedUtterances > 0) queuedUtterances--;
                    speechDone(gen);
                }

                @Override
                public void onError(int gen, String text, String message) {
                    if (gen != speechGen) return;
                    // Nicht stumm bleiben: mit der Handy-Stimme weiter, einmal pro Sitzung Bescheid geben.
                    testLog("pocket fehler: " + message);
                    pocketFailed = true;
                    if (queuedUtterances > 0) queuedUtterances--;
                    if (!pocketWarned) {
                        pocketWarned = true;
                        statusBriefly(message + " Ich spreche mit der Handy-Stimme weiter.");
                    }
                    speakDevice(text, gen, false); // hinten anstellen: Jeder ausgefallene Satz kommt genau einmal
                }
            });
            pocket.setVoiceCall(speechAsCall);
            pocket.prepare();
        }
        return pocket;
    }

    private boolean pocketWarned;
    /** Offline-Stimme hat in dieser Sitzung versagt – bis zum Neustart nicht mehr versuchen. */
    private boolean pocketFailed;
    private void speakPocket(String clean, int gen, boolean flush) {
        if (gen != speechGen) return;
        setState(State.SPEAKING);
        pocket.setVoiceCall(speechAsCall);
        queuedUtterances++;
        pocket.speak(clean, gen, flush);
    }

    private void onSpeechFinished() {
        testLog("sprache fertig");
        lastExchangeAt = SystemClock.elapsedRealtime();
        if (state == State.SPEAKING) setState(State.IDLE);
        subtitles.hideAfter(CAPTION_LINGER_MS);
        scheduleRelisten(RELISTEN_DELAY_MS);
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
        String text = prefs.alwaysListen() && !listenPaused
                ? "Ich höre zu – einfach losreden."
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
