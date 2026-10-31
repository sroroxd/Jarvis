package de.art.jarvis;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Das Herz von Jarvis: schwebendes „J“ über allen Apps, Zuhören, Bildschirm sehen,
 * Claude fragen und die Antwort vorlesen.
 */
public class JarvisService extends Service {
    public static final String ACTION_START = "de.art.jarvis.START";
    public static final String ACTION_LISTEN = "de.art.jarvis.LISTEN";
    public static final String ACTION_STOP = "de.art.jarvis.STOP";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_DATA = "data";

    private static final String CHANNEL = "jarvis";
    private static final int NOTIFICATION_ID = 1;
    private static final long CONVERSATION_TIMEOUT_MS = 15 * 60 * 1000L;
    private static final int MAX_HISTORY = 16;

    private static final int COLOR_IDLE = 0xFF00B4D8;
    private static final int COLOR_LISTENING = 0xFFE63946;
    private static final int COLOR_THINKING = 0xFFF4A261;
    private static final int COLOR_SPEAKING = 0xFF2A9D8F;

    private static volatile boolean running;

    public static boolean isRunning() {
        return running;
    }

    private enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<JSONObject> history = new ArrayList<>();
    private final Runnable hidePanel = this::hidePanel;

    private State state = State.IDLE;
    private Prefs prefs;
    private ClaudeClient claude;
    private GeminiClient gemini;
    private WindowManager wm;
    private MediaProjection projection;
    private ScreenGrabber grabber;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private TextView bubble;
    private TextView panel;
    private WindowManager.LayoutParams bubbleLp;
    private WindowManager.LayoutParams panelLp;
    private long lastInteraction;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        claude = new ClaudeClient();
        gemini = new GeminiClient();
        wm = getSystemService(WindowManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Jarvis", NotificationManager.IMPORTANCE_LOW));
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int result = tts.setLanguage(Locale.GERMANY);
            ttsReady = result >= TextToSpeech.LANG_AVAILABLE;
            pickBestGermanVoice();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { main.post(JarvisService.this::onSpeechFinished); }
                @Override public void onError(String id) { main.post(JarvisService.this::onSpeechFinished); }
            });
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            running = true;
            if (projection == null) {
                int code = intent.getIntExtra(EXTRA_CODE, 0);
                @SuppressWarnings("deprecation")
                Intent data = intent.getParcelableExtra(EXTRA_DATA);
                if (data != null) startProjection(code, data);
            }
            if (bubble == null) showOverlay();
            main.postDelayed(() -> say("Zu Diensten, Sir."), 600);
            return START_NOT_STICKY;
        }
        if (ACTION_LISTEN.equals(action)) {
            if (running) onBubbleTap();
            else stopSelf();
        }
        return START_NOT_STICKY;
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
            showPanel("Bildschirmfreigabe fehlgeschlagen – ich arbeite vorerst ohne Bild.");
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
                });
            }
        }, main);
        Rect b = wm.getCurrentWindowMetrics().getBounds();
        grabber = new ScreenGrabber(projection);
        grabber.start(b.width(), b.height(), getResources().getDisplayMetrics().densityDpi);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (grabber != null) {
            Rect b = wm.getCurrentWindowMetrics().getBounds();
            grabber.resize(b.width(), b.height(), getResources().getDisplayMetrics().densityDpi);
        }
    }

    // ---------------------------------------------------------------- Overlay

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) return;
        int size = dp(56);
        bubble = new TextView(this);
        bubble.setText("J");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(22);
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(circle(COLOR_IDLE));
        bubble.setElevation(dp(6));
        bubbleLp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = dp(8);
        bubbleLp.y = dp(220);
        bubble.setOnTouchListener(new DragOrTap());
        wm.addView(bubble, bubbleLp);

        panel = new TextView(this);
        panel.setTextColor(Color.WHITE);
        panel.setTextSize(15);
        panel.setMaxWidth(dp(300));
        panel.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE60B1D2A);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), COLOR_IDLE);
        panel.setBackground(bg);
        panel.setVisibility(View.GONE);
        panel.setOnClickListener(v -> {
            if (state == State.SPEAKING && tts != null) tts.stop();
            if (state == State.SPEAKING) setState(State.IDLE);
            hidePanel();
        });
        panelLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        wm.addView(panel, panelLp);
    }

    private final class DragOrTap implements View.OnTouchListener {
        private float downX, downY;
        private int startX, startY;
        private long downTime;
        private boolean dragging;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = bubbleLp.x;
                    startY = bubbleLp.y;
                    downTime = System.currentTimeMillis();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > dp(10)) dragging = true;
                    if (dragging) {
                        bubbleLp.x = startX + (int) dx;
                        bubbleLp.y = startY + (int) dy;
                        wm.updateViewLayout(bubble, bubbleLp);
                        if (panel.getVisibility() == View.VISIBLE) positionPanel();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) {
                        if (System.currentTimeMillis() - downTime > 700) resetConversation();
                        else onBubbleTap();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private void positionPanel() {
        int screenWidth = wm.getCurrentWindowMetrics().getBounds().width();
        boolean bubbleOnRight = bubbleLp.x + dp(28) > screenWidth / 2;
        if (bubbleOnRight) {
            panelLp.gravity = Gravity.TOP | Gravity.END;
            panelLp.x = screenWidth - bubbleLp.x + dp(8);
        } else {
            panelLp.gravity = Gravity.TOP | Gravity.START;
            panelLp.x = bubbleLp.x + dp(64);
        }
        panelLp.y = bubbleLp.y;
        wm.updateViewLayout(panel, panelLp);
    }

    private void showPanel(String text) {
        if (panel == null) return;
        main.removeCallbacks(hidePanel);
        panel.setText(text);
        panel.setVisibility(View.VISIBLE);
        positionPanel();
    }

    private void hidePanel() {
        if (panel != null) panel.setVisibility(View.GONE);
    }

    private void setOverlayVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.INVISIBLE;
        if (bubble != null) bubble.setVisibility(v);
        if (panel != null) panel.setVisibility(v);
    }

    private void setState(State s) {
        state = s;
        if (bubble == null) return;
        int color;
        switch (s) {
            case LISTENING: color = COLOR_LISTENING; break;
            case THINKING: color = COLOR_THINKING; break;
            case SPEAKING: color = COLOR_SPEAKING; break;
            default: color = COLOR_IDLE;
        }
        bubble.setBackground(circle(color));
    }

    // ---------------------------------------------------------------- Ablauf

    private void onBubbleTap() {
        switch (state) {
            case LISTENING:
                if (recognizer != null) recognizer.stopListening();
                break;
            case THINKING:
                showPanel("Einen Moment, Sir – ich denke noch nach …");
                break;
            case SPEAKING:
                if (tts != null) tts.stop();
                startListening();
                break;
            default:
                startListening();
        }
    }

    private void resetConversation() {
        history.clear();
        if (tts != null) tts.stop();
        setState(State.IDLE);
        say("Neues Gespräch.");
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showPanel("Mikrofon-Berechtigung fehlt – bitte die Jarvis-App öffnen.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showPanel("Auf diesem Handy ist keine Spracherkennung verfügbar.");
            return;
        }
        destroyRecognizer();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override
            public void onPartialResults(Bundle partial) {
                String text = firstResult(partial);
                if (!text.isEmpty()) showPanel(text + " …");
            }

            @Override
            public void onResults(Bundle results) {
                String text = firstResult(results);
                main.post(JarvisService.this::destroyRecognizer);
                if (text.isEmpty()) {
                    setState(State.IDLE);
                    showPanel("Ich habe nichts verstanden.");
                    main.postDelayed(hidePanel, 3000);
                } else {
                    handleUtterance(text);
                }
            }

            @Override
            public void onError(int error) {
                main.post(JarvisService.this::destroyRecognizer);
                setState(State.IDLE);
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    showPanel("Ich habe nichts verstanden.");
                } else {
                    showPanel("Spracherkennung meldet Fehler " + error + ". Tipp einfach nochmal auf mich.");
                }
                main.postDelayed(hidePanel, 4000);
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        setState(State.LISTENING);
        showPanel("Ich höre, Sir …");
        recognizer.startListening(i);
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
        String key = prefs.apiKey();
        if (key.isEmpty()) {
            setState(State.IDLE);
            say("Mir fehlt noch der API-Schlüssel. Bitte trag ihn in der Jarvis-App ein.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastInteraction > CONVERSATION_TIMEOUT_MS) history.clear();
        lastInteraction = now;

        setState(State.THINKING);
        showPanel("„" + text + "“\n…");
        if (grabber == null) {
            ask(key, text, null);
            return;
        }
        // Overlay kurz ausblenden, damit es nicht auf dem Bildschirmfoto landet.
        setOverlayVisible(false);
        main.postDelayed(() -> {
            String image = grabber == null ? null : grabber.captureJpegBase64();
            setOverlayVisible(true);
            ask(key, text, image);
        }, 300);
    }

    private void ask(String key, String text, String image) {
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

        String system = Persona.system(prefs.tftMode(), image != null);
        ClaudeClient.Callback callback = new ClaudeClient.Callback() {
            @Override
            public void onResult(String answer) {
                if (answer.isEmpty()) answer = "Darauf habe ich gerade keine Antwort, Sir.";
                remember("user", image != null ? "[Bildschirmfoto war angehängt] " + text : text);
                remember("assistant", answer);
                speak(answer);
            }

            @Override
            public void onError(String message) {
                setState(State.IDLE);
                say(message);
            }
        };
        if (prefs.isGemini()) gemini.send(key, prefs.model(), system, messages, callback);
        else claude.send(key, prefs.model(), system, messages, callback);
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

    private void say(String text) {
        speak(text);
    }

    private void speak(String text) {
        showPanel(text);
        if (!ttsReady) {
            setState(State.IDLE);
            main.postDelayed(hidePanel, 15000);
            return;
        }
        String clean = text.replaceAll("[*#_`>]", "").trim();
        int max = TextToSpeech.getMaxSpeechInputLength() - 1;
        if (clean.length() > max) clean = clean.substring(0, max);
        setState(State.SPEAKING);
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis-" + System.nanoTime());
    }

    private void onSpeechFinished() {
        if (state == State.SPEAKING) setState(State.IDLE);
        main.removeCallbacks(hidePanel);
        main.postDelayed(hidePanel, 12000);
    }

    private void pickBestGermanVoice() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            Voice best = null;
            for (Voice v : voices) {
                if (!"de".equals(v.getLocale().getLanguage())) continue;
                Set<String> features = v.getFeatures();
                if (features != null && features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                if (best == null || v.getQuality() > best.getQuality()) best = v;
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) {
            // Standardstimme bleibt aktiv.
        }
    }

    // ---------------------------------------------------------------- Hilfen

    private Notification buildNotification() {
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, JarvisService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_jarvis)
                .setContentTitle("Jarvis ist aktiv")
                .setContentText("Tippe auf das J zum Sprechen. Lange drücken = neues Gespräch.")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_jarvis), "Beenden", stop).build())
                .build();
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(dp(2), 0x55FFFFFF);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        running = false;
        main.removeCallbacksAndMessages(null);
        destroyRecognizer();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (bubble != null) wm.removeView(bubble);
        if (panel != null) wm.removeView(panel);
        bubble = null;
        panel = null;
        if (grabber != null) grabber.stop();
        grabber = null;
        if (projection != null) projection.stop();
        projection = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
