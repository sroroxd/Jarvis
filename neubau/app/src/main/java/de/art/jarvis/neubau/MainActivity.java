package de.art.jarvis.neubau;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Eigenständiger, kostenloser Sprachkanal-Test: Mikrofon -> Offline-Spracherkennung
 * -> erkennbare Testantwort -> TTS -> automatisch weiter zuhören.
 * Noch KEIN KI-Dialog. Kein Zugriff auf den Code oder die Schlüssel der alten App.
 */
public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQUEST_MIC = 4;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView transcript;
    private Button toggle;
    private TextToSpeech speech;
    private SpeechRecognizer recognizer;
    private boolean voiceReady;
    private boolean running;
    private int recognitionErrors;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 44, 36, 24);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView heading = new TextView(this);
        heading.setText("JARVIS · NEUBAU");
        heading.setTextSize(26);
        heading.setGravity(Gravity.CENTER);
        layout.addView(heading);

        TextView disclosure = new TextView(this);
        disclosure.setText("Phase 1: Kostenloser Test von Mikrofon, Offline-Erkennung und Stimme. Noch keine KI-Antworten.");
        disclosure.setTextSize(16);
        disclosure.setPadding(0, 24, 0, 24);
        layout.addView(disclosure);

        status = new TextView(this);
        status.setTextSize(18);
        status.setText("Stimme wird vorbereitet …");
        layout.addView(status);

        toggle = new Button(this);
        toggle.setText("Sprachtest starten");
        toggle.setEnabled(false);
        toggle.setOnClickListener(v -> {
            if (running) stopSession();
            else startSession();
        });
        layout.addView(toggle);

        transcript = new TextView(this);
        transcript.setTextSize(16);
        transcript.setPadding(0, 32, 0, 16);
        transcript.setText("Gesprächsprobe erscheint hier.");
        layout.addView(transcript);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);

        speech = new TextToSpeech(this, this);
    }

    @Override public void onInit(int result) {
        voiceReady = result == TextToSpeech.SUCCESS;
        if (voiceReady) {
            int language = speech.setLanguage(Locale.GERMAN);
            voiceReady = language != TextToSpeech.LANG_MISSING_DATA
                    && language != TextToSpeech.LANG_NOT_SUPPORTED;
            speech.setSpeechRate(0.98f);
            speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    main.postDelayed(() -> { if (running) listen(); }, 550);
                }
                @Override public void onError(String id) {
                    main.post(() -> {
                        if (running) {
                            status.setText("Sprachausgabe fehlgeschlagen – neuer Versuch.");
                            main.postDelayed(() -> { if (running) listen(); }, 900);
                        }
                    });
                }
            });
        }
        status.setText(voiceReady
                ? "Bereit für den kostenlosen Sprachtest."
                : "Keine deutsche Vorlesestimme gefunden. Bitte TTS-Stimme installieren.");
        toggle.setEnabled(voiceReady);
    }

    private void startSession() {
        if (!voiceReady) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            status.setText("Keine Offline-Spracherkennung auf dem Gerät verfügbar.");
            return; // Keine stillschweigende Umschaltung auf Cloud-Spracherkennung.
        }
        running = true;
        recognitionErrors = 0;
        toggle.setText("Sprachtest beenden");
        listen();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == REQUEST_MIC) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startSession();
            else status.setText("Mikrofonfreigabe fehlt.");
        }
    }

    private void listen() {
        if (!running) return;
        destroyRecognizer();
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            final SpeechRecognizer current = recognizer;
            current.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) {
                    if (current == recognizer) status.setText("Ich höre zu …");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rms) {}
                @Override public void onBufferReceived(byte[] bytes) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onEvent(int code, Bundle b) {}
                @Override public void onPartialResults(Bundle b) {
                    if (current != recognizer) return;
                    String first = firstSpeech(b);
                    if (!first.isEmpty()) transcript.setText("Du: " + first + " …");
                }
                @Override public void onResults(Bundle b) {
                    if (current != recognizer) return;
                    String first = firstSpeech(b);
                    destroyRecognizer();
                    recognitionErrors = 0;
                    if (!running) return;
                    if (first.isEmpty()) {
                        main.postDelayed(() -> { if (running) listen(); }, 300);
                        return;
                    }
                    String reply = AudioProbe.reply(first);
                    transcript.setText("Du: " + first + "\nJARVIS-Test: " + reply);
                    status.setText("Ich spreche …");
                    speech.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "probe");
                }
                @Override public void onError(int code) {
                    if (current != recognizer) return;
                    destroyRecognizer();
                    if (!running) return;
                    boolean quiet = code == SpeechRecognizer.ERROR_NO_MATCH
                            || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                    recognitionErrors = quiet ? 0 : Math.min(recognitionErrors + 1, 6);
                    long retry = quiet ? 250L : Math.min(500L * (1L << recognitionErrors), 8000L);
                    status.setText(quiet ? "Stille erkannt – höre weiter zu."
                            : "Spracherkennung: Fehler " + code + ", erneuter Versuch.");
                    main.postDelayed(() -> { if (running) listen(); }, retry);
                }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            current.startListening(intent);
        } catch (RuntimeException e) {
            destroyRecognizer();
            running = false;
            toggle.setText("Sprachtest starten");
            status.setText("Offline-Erkennung kann nicht gestartet werden: " + e.getClass().getSimpleName());
        }
    }

    private static String firstSpeech(Bundle b) {
        if (b == null) return "";
        ArrayList<String> matches = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return matches == null || matches.isEmpty() || matches.get(0) == null
                ? "" : matches.get(0).trim();
    }

    private void destroyRecognizer() {
        SpeechRecognizer old = recognizer;
        recognizer = null;
        if (old != null) old.destroy();
    }

    private void stopSession() {
        running = false;
        main.removeCallbacksAndMessages(null);
        destroyRecognizer();
        if (speech != null) speech.stop();
        toggle.setText("Sprachtest starten");
        status.setText("Sprachtest beendet.");
    }

    @Override protected void onStop() {
        super.onStop();
        if (running) stopSession(); // Kein verborgenes Mikrofon im Hintergrund.
    }

    @Override protected void onDestroy() {
        stopSession();
        if (speech != null) speech.shutdown();
        super.onDestroy();
    }
}
