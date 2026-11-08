package de.art.jarvis;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.art.jarvis.databinding.ActivitySettingsBinding;

/** Alle Einstellungen, übersichtlich in Gruppen. Änderungen gelten sofort. */
public class SettingsActivity extends AppCompatActivity {
    private static final String PREVIEW_TEXT = "Guten Abend, Sir. Alle Systeme sind einsatzbereit.";

    private ActivitySettingsBinding b;
    private Prefs prefs;
    /**
     * Gedächtnis, wie es zuletzt im Feld angezeigt wurde. Gespeichert wird das Feld nur, wenn Art es geändert
     * hat – sonst überschrieb das Verlassen der Einstellungen, was Jarvis sich inzwischen gemerkt hatte
     * („Jarvis, merk dir …“, während die Einstellungen offen sind).
     */
    private String shownMemory = "";

    private ElevenTts elevenPreview;
    private TextToSpeech previewTts;
    private boolean previewReady;
    private String previewEngine;
    private List<Voices.Engine> engines = new ArrayList<>();
    private List<Voice> voices = new ArrayList<>();

    private final ActivityResultLauncher<String> backupCreate =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::onBackupCreated);
    private final ActivityResultLauncher<String[]> backupOpen =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onBackupOpened);
    private final ActivityResultLauncher<Intent> overlayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                updateOverlayWarning();
                refreshService();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = new Prefs(this);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        setupBrain();
        setupVoiceControl();
        setupVoice();
        setupDisplay();
        setupGame();
        setupWishes();
        setupAgent();
        setupBackup();
        b.versionText.setText("Jarvis " + versionName());
        hideTicks(b.sliderSensitivity, b.sliderBargeIn, b.sliderPause, b.sliderRate, b.sliderPitch);
    }

    /** Ohne die Rasterpunkte wirken die Regler ruhiger. */
    @SuppressWarnings("deprecation")
    private static void hideTicks(Slider... sliders) {
        for (Slider s : sliders) s.setTickVisible(false);
    }

    // ---------------------------------------------------------------- Gehirn (KI)

    private void setupBrain() {
        b.keyField.setText(prefs.apiKey());
        b.keyLayout.setHelperText(prefs.apiKeyPreview());
        b.modelField.setText(prefs.rawModel());
        b.testKey.setOnClickListener(v -> testKey());
    }

    /** Kleine Testanfrage (kostet Bruchteile eines Cents) und zeigt das genaue Ergebnis. */
    private void testKey() {
        saveTextFields();
        if (prefs.apiKey().isEmpty()) {
            showResult(b.keyResult, "Bitte zuerst einen Schlüssel einfügen.");
            return;
        }
        showResult(b.keyResult, "Teste Schlüssel …");
        JSONArray messages = new JSONArray();
        try {
            messages.put(new JSONObject().put("role", "user").put("content", "Antworte nur mit: OK"));
        } catch (JSONException ignored) {
            return;
        }
        ClaudeClient.Callback cb = new ClaudeClient.Callback() {
            @Override
            public void onResult(String text) {
                showResult(b.keyResult, "✓ Schlüssel funktioniert (Modell " + prefs.model() + ").");
            }

            @Override
            public void onError(String message) {
                showResult(b.keyResult, "✗ " + message);
            }
        };
        if (prefs.isGemini()) new GeminiClient().send(prefs.apiKey(), prefs.model(), "Test.", messages, cb);
        else new ClaudeClient().send(prefs.apiKey(), prefs.model(), "Test.", messages, cb);
    }

    // ---------------------------------------------------------------- Sprachsteuerung

    private void setupVoiceControl() {
        b.switchListen.setChecked(prefs.alwaysListen());
        b.switchListen.setOnCheckedChangeListener((v, on) -> {
            prefs.setAlwaysListen(on);
            refreshService();
        });

        setSlider(b.sliderSensitivity, prefs.sensitivity());
        b.sensitivityValue.setText(String.valueOf(prefs.sensitivity()));
        b.sliderSensitivity.setLabelFormatter(v -> String.valueOf(Math.round(v)));
        b.sliderSensitivity.addOnChangeListener((s, value, fromUser) -> {
            b.sensitivityValue.setText(String.valueOf(Math.round(value)));
            if (fromUser) prefs.setSensitivity(Math.round(value));
        });
        b.sliderSensitivity.addOnSliderTouchListener(onRelease(this::refreshService));

        b.switchEarcon.setChecked(prefs.earcon());
        b.switchEarcon.setOnCheckedChangeListener((v, on) -> prefs.setEarcon(on));
        b.switchBargeIn.setChecked(prefs.bargeIn());
        b.switchBargeIn.setOnCheckedChangeListener((v, on) -> {
            prefs.setBargeIn(on);
            refreshService();
        });
        b.switchEchoCall.setChecked(prefs.echoCallMode());
        b.switchEchoCall.setOnCheckedChangeListener((v, on) -> {
            prefs.setEchoCallMode(on);
            refreshService();
        });
        setSlider(b.sliderBargeIn, prefs.bargeInSensitivity());
        b.bargeInValue.setText(String.valueOf(prefs.bargeInSensitivity()));
        b.sliderBargeIn.setLabelFormatter(v -> String.valueOf(Math.round(v)));
        b.sliderBargeIn.addOnChangeListener((sl, value, fromUser) -> {
            b.bargeInValue.setText(String.valueOf(Math.round(value)));
            if (fromUser) prefs.setBargeInSensitivity(Math.round(value));
        });

        setSlider(b.sliderPause, prefs.pauseMs() / 1000f);
        b.pauseValue.setText(seconds(prefs.pauseMs() / 1000f));
        b.sliderPause.setLabelFormatter(this::seconds);
        b.sliderPause.addOnChangeListener((s, value, fromUser) -> {
            b.pauseValue.setText(seconds(value));
            if (fromUser) prefs.setPauseMs(Math.round(value * 1000));
        });
    }

    // ---------------------------------------------------------------- Stimme

    private void setupVoice() {
        elevenPreview = new ElevenTts(this);
        engines = Voices.engines(this);
        String active = Voices.resolveEngine(this, prefs);
        String[] labels = new String[Math.max(1, engines.size())];
        int selected = -1;
        for (int i = 0; i < engines.size(); i++) {
            labels[i] = engines.get(i).label;
            if (engines.get(i).pkg.equals(active)) selected = i;
        }
        if (engines.isEmpty()) labels[0] = "Systemstandard";
        b.engineDropdown.setSimpleItems(labels);
        b.engineDropdown.setText(selected >= 0 ? labels[selected] : labels[0], false);
        b.engineDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= engines.size()) return;
            String pkg = engines.get(position).pkg;
            if (pkg.equals(previewEngine)) return;
            prefs.setEngine(pkg);
            prefs.setVoiceName(""); // neue Engine → wieder automatisch wählen
            loadPreviewEngine(pkg);
        });

        setSlider(b.sliderRate, prefs.rate());
        setSlider(b.sliderPitch, prefs.pitch());
        b.rateValue.setText(decimal(prefs.rate()));
        b.pitchValue.setText(pitchText(prefs.pitch()));
        b.sliderRate.setLabelFormatter(this::decimal);
        b.sliderPitch.setLabelFormatter(this::decimal);
        b.sliderRate.addOnChangeListener((s, value, fromUser) -> {
            b.rateValue.setText(decimal(value));
            if (fromUser) prefs.setRate(value);
        });
        b.sliderPitch.addOnChangeListener((s, value, fromUser) -> {
            b.pitchValue.setText(pitchText(value));
            if (fromUser) prefs.setPitch(value);
        });

        b.elevenKeyField.setText(prefs.elevenKey());
        b.elevenVoiceField.setText(prefs.rawElevenVoice());
        b.previewVoice.setOnClickListener(v -> preview());
        loadPreviewEngine(active);
    }

    private void preview() {
        saveTextFields();
        if (!prefs.elevenKey().isEmpty()) {
            showResult(b.voiceResult, "Premium-Stimme wird geladen …");
            elevenPreview.speak(prefs.elevenKey(), prefs.elevenVoice(), PREVIEW_TEXT, prefs.rate(), new ElevenTts.Listener() {
                @Override public void onStart() { showResult(b.voiceResult, "✓ Premium-Stimme aktiv."); }
                @Override public void onDone() { }
                @Override public void onError(String message) { showResult(b.voiceResult, "✗ " + message); }
            });
            return;
        }
        if (previewTts == null || !previewReady) {
            showResult(b.voiceResult, "Sprachausgabe lädt noch …");
            return;
        }
        b.voiceResult.setVisibility(View.GONE);
        Voices.apply(previewTts, prefs, previewEngine);
        previewTts.speak(Text.sirForDeviceVoice(PREVIEW_TEXT), TextToSpeech.QUEUE_FLUSH, null, "preview");
    }

    /** Startet die Probe-Sprachausgabe mit der Engine und füllt die Stimmenliste. */
    private void loadPreviewEngine(String engine) {
        if (previewTts != null) previewTts.shutdown();
        previewReady = false;
        previewEngine = engine;
        b.voiceDropdown.setSimpleItems(new String[]{"Stimmen werden geladen …"});
        b.voiceDropdown.setText("Stimmen werden geladen …", false);
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = Voices.create(this, engine, status -> {
            if (holder[0] != previewTts || isFinishing()) return;
            if (status != TextToSpeech.SUCCESS) {
                b.voiceDropdown.setText("Diese Sprachausgabe startet nicht – bitte eine andere wählen.", false);
                return;
            }
            previewReady = true;
            voices = Voices.list(previewTts);
            Voice chosen = Voices.choose(previewTts, prefs, engine);
            String[] labels = new String[Math.max(1, voices.size())];
            int selected = 0;
            for (int i = 0; i < voices.size(); i++) {
                labels[i] = Voices.label(voices.get(i));
                if (chosen != null && voices.get(i).getName().equals(chosen.getName())) selected = i;
            }
            if (voices.isEmpty()) labels[0] = "Standardstimme der Engine";
            b.voiceDropdown.setSimpleItems(labels);
            b.voiceDropdown.setText(labels[selected], false);
            b.voiceDropdown.setOnItemClickListener((parent, view, position, id) -> {
                if (position < voices.size()) prefs.setVoiceName(voices.get(position).getName());
            });
        });
        previewTts = holder[0];
    }

    // ---------------------------------------------------------------- Anzeige

    private void setupDisplay() {
        b.switchFloatingJ.setChecked(prefs.floatingJ());
        b.switchFloatingJ.setOnCheckedChangeListener((v, on) -> {
            prefs.setFloatingJ(on);
            if (on && !Settings.canDrawOverlays(this)) requestOverlay();
            updateOverlayWarning();
            refreshService();
        });
        b.switchSubtitles.setChecked(prefs.subtitles());
        b.switchSubtitles.setOnCheckedChangeListener((v, on) -> {
            prefs.setSubtitles(on);
            updateOverlayWarning();
            refreshService();
        });
        b.switchSubtitlesGame.setChecked(prefs.subtitlesInGame());
        b.switchSubtitlesGame.setOnCheckedChangeListener((v, on) -> {
            prefs.setSubtitlesInGame(on);
            updateOverlayWarning();
            refreshService();
        });
        b.positionGroup.check(prefs.subtitlesTop() ? R.id.positionTop : R.id.positionBottom);
        b.positionGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) prefs.setSubtitlesTop(id == R.id.positionTop);
        });
        b.grantOverlay.setOnClickListener(v -> requestOverlay());
        updateOverlayWarning();
    }

    private void requestOverlay() {
        overlayLauncher.launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }

    private void updateOverlayWarning() {
        boolean needed = prefs.floatingJ() || prefs.subtitles() || prefs.subtitlesInGame();
        b.overlayWarning.setVisibility(needed && !Settings.canDrawOverlays(this) ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- Spielmodus

    private void setupGame() {
        updateMetaInfo();
        b.metaUpdate.setOnClickListener(v -> updateMetaNow());
        b.switchGame.setChecked(prefs.tftMode());
        b.switchGame.setOnCheckedChangeListener((v, on) -> {
            prefs.setTftMode(on);
            refreshService();
        });
        b.switchProactive.setChecked(prefs.proactiveCoach());
        b.switchProactive.setOnCheckedChangeListener((v, on) -> {
            prefs.setProactiveCoach(on);
            refreshService();
        });
        b.switchGameThink.setChecked(prefs.gameThinkDeep());
        b.switchGameThink.setOnCheckedChangeListener((v, on) -> {
            prefs.setGameThinkDeep(on);
            refreshService();
        });
        b.switchGameAuto.setChecked(prefs.gameAuto());
        b.switchGameAuto.setOnCheckedChangeListener((v, on) -> {
            prefs.setGameAuto(on);
            if (on && !GameWatch.permitted(this)) openUsageAccess();
            updateUsageAccess();
            refreshService();
        });
        b.grantUsageAccess.setOnClickListener(v -> openUsageAccess());
        updateUsageAccess();
        b.batteryExempt.setOnClickListener(v -> requestBatteryExemption());
        updateBattery();
    }

    /** Hinweis „Im Hintergrund weiterlaufen lassen“ nur, solange Jarvis der Akku-Optimierung unterliegt. */
    private void updateBattery() {
        android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
        boolean exempt = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        b.batteryBox.setVisibility(exempt ? View.GONE : View.VISIBLE);
    }

    /** Systemdialog „Akku-Optimierung ignorieren?“ direkt für Jarvis – sonst die Liste aller Apps. */
    private void requestBatteryExemption() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception direct) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                // sehr ungewöhnlich – der Hinweis bleibt sichtbar
            }
        }
    }

    /** Knopf „Nutzungsdatenzugriff erlauben“ nur, solange die Erkennung an ist und die Freigabe fehlt. */
    private void updateUsageAccess() {
        b.grantUsageAccess.setVisibility(prefs.gameAuto() && !GameWatch.permitted(this) ? View.VISIBLE : View.GONE);
    }

    /** Systemseite „Nutzungsdatenzugriff“ – möglichst direkt bei Jarvis, sonst die Liste aller Apps. */
    private void openUsageAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Exception direct) {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Exception ignored) {
                // sehr ungewöhnlich – der Knopf bleibt sichtbar
            }
        }
    }

    // ---------------------------------------------------------------- Wünsche an die Entwicklung

    /**
     * GitHub-Formular für einen fein eingeschränkten Schlüssel, vorausgefüllt per URL (laut GitHub-Doku:
     * name, description, target_name, expires_in und Berechtigungen). Die Auswahl des Repos lässt
     * GitHub nicht vorausfüllen – das tippt Art selbst an.
     */
    private static final String GITHUB_TOKEN_TEMPLATE = "https://github.com/settings/personal-access-tokens/new"
            + "?name=Jarvis+W%C3%BCnsche"
            + "&description=Jarvis+legt+W%C3%BCnsche+als+Aufgaben+in+sroroxd%2FJarvis+an"
            + "&target_name=sroroxd&expires_in=365&issues=write";

    /** Stand der Issues auf GitHub (null = noch nicht geladen). */
    private java.util.Map<Integer, String> wishStates;
    private boolean wishStatesLoading;

    /** Wunschliste anzeigen; den echten Stand auf GitHub einmal pro Öffnen der Einstellungen nachladen. */
    private void showWishes() {
        b.wishList.setText(Wishes.display(prefs.wishes(), wishStates));
        if (wishStates != null || wishStatesLoading) return;
        wishStatesLoading = true;
        new WishSender().fetchStates(prefs.githubToken(), states -> {
            wishStatesLoading = false;
            if (states == null || isFinishing() || isDestroyed()) return;
            wishStates = states;
            b.wishList.setText(Wishes.display(prefs.wishes(), wishStates));
        });
    }

    private void setupWishes() {
        b.githubTokenField.setText(prefs.githubToken());
        b.githubTokenLayout.setHelperText(prefs.githubTokenPreview());
        showWishes();
        b.wishSend.setOnClickListener(v -> sendOpenWishes());
        b.githubTokenCreate.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_TOKEN_TEMPLATE)));
            } catch (Exception ignored) {
                showResult(b.wishResult, "✗ Kein Browser gefunden.");
            }
        });
    }

    /** Schickt alle noch offenen Wünsche als Aufgaben an GitHub. */
    private void sendOpenWishes() {
        prefs.setGithubToken(text(b.githubTokenField));
        b.githubTokenLayout.setHelperText(prefs.githubTokenPreview());
        String token = prefs.githubToken();
        if (token.isEmpty()) {
            showResult(b.wishResult, "✗ Erst den GitHub-Schlüssel eintragen.");
            return;
        }
        java.util.List<Wishes.Wish> open = new java.util.ArrayList<>();
        for (Wishes.Wish w : Wishes.load(prefs.wishes())) if (w.open()) open.add(w);
        if (open.isEmpty()) {
            showResult(b.wishResult, "Keine offenen Wünsche.");
            return;
        }
        showResult(b.wishResult, "Sende " + open.size() + " …");
        WishSender sender = new WishSender();
        int[] left = {open.size()};
        String[] lastError = {null};
        for (Wishes.Wish w : open) {
            sender.send(token, w.title, w.body, JarvisService.today(), (issue, error) -> {
                if (issue > 0) prefs.setWishes(Wishes.markSent(prefs.wishes(), w.title, issue));
                else lastError[0] = error;
                if (--left[0] == 0) {
                    Backup.autoSave(this, prefs);
                    wishStates = null; // neue Aufgaben: Stand neu laden
                    showWishes();
                    showResult(b.wishResult, lastError[0] == null ? "✓ Alle gesendet." : "✗ " + lastError[0]);
                }
            });
        }
    }

    // ---------------------------------------------------------------- Agent-Rückgrat

    private void setupAgent() {
        b.agentTokenField.setText(prefs.agentToken());
        b.switchAgent.setChecked(prefs.agentEnabled());
        b.switchAgent.setOnCheckedChangeListener((v, on) -> {
            prefs.setAgentEnabled(on);
            updateAgentInfo();
            refreshService();
        });
        b.agentTest.setOnClickListener(v -> testAgent());
        updateAgentInfo();
    }

    private void updateAgentInfo() {
        if (!prefs.agentEnabled()) {
            b.agentInfo.setText("Aus – Aufträge gehen an die Claude-API wie bisher.");
        } else if (prefs.agentToken().isEmpty()) {
            b.agentInfo.setText("An, aber ohne Token. Trag das Token ein, sonst weist der Agent jeden Auftrag ab.");
        } else {
            b.agentInfo.setText("An, Port " + prefs.agentPort() + ", Token " + prefs.agentTokenPreview() + ".");
        }
    }

    /** Fragt den Agenten, ob er läuft. Antwort steht direkt unter dem Knopf. */
    private void testAgent() {
        prefs.setAgentToken(text(b.agentTokenField));
        updateAgentInfo();
        showResult(b.agentInfo, "Prüfe …");
        new AgentClient().pruefen(prefs.agentPort(), prefs.agentToken(),
                reply -> showResult(b.agentInfo, reply.ok() ? "✓ " + reply.antwort : "✗ " + reply.fehler));
    }

    // ---------------------------------------------------------------- Gedächtnis und Sicherung

    private void setupBackup() {
        showMemory();
        b.switchBackupKeys.setChecked(prefs.backupKeys());
        b.switchBackupKeys.setOnCheckedChangeListener((v, on) -> {
            prefs.setBackupKeys(on);
            Backup.autoSave(this, prefs);
        });
        updateBackupInfo();
        b.backupCreate.setOnClickListener(v -> {
            saveTextFields();
            backupCreate.launch(Backup.FILE_NAME);
        });
        b.backupOpen.setOnClickListener(v -> backupOpen.launch(new String[]{"*/*"}));
    }

    private void onBackupCreated(Uri uri) {
        if (uri == null) return;
        useAsBackupTarget(uri);
        boolean ok = Backup.write(this, prefs, uri);
        b.backupInfo.setText(ok ? "✓ Sicherung eingerichtet. Sie aktualisiert sich ab jetzt automatisch."
                : "✗ Die Sicherungsdatei ließ sich nicht schreiben.");
    }

    private void onBackupOpened(Uri uri) {
        if (uri == null) return;
        try {
            int count = Backup.apply(prefs, Backup.read(this, uri));
            // Die Test-App liest eine Sicherung nur – sie schreibt nie zurück, damit die Sicherung
            // der normalen Jarvis-App unverändert bleibt.
            if (!BuildConfig.TEST_APP) useAsBackupTarget(uri); // künftig in dieselbe Datei sichern
            refreshFromPrefs();
            b.backupInfo.setText("✓ Wiederhergestellt: " + count + " Gedächtniseinträge und deine Einstellungen."
                    + (BuildConfig.TEST_APP ? " Die Test-App ändert diese Datei nicht." : ""));
            refreshService();
        } catch (Exception e) {
            b.backupInfo.setText("✗ Das ist keine gültige Jarvis-Sicherung.");
        }
    }

    /** Merkt sich die Datei dauerhaft als Sicherungsziel (Zugriff bleibt auch nach Neustarts erhalten). */
    private void useAsBackupTarget(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            prefs.setBackupUri(uri.toString());
        } catch (Exception e) {
            prefs.setBackupUri("");
        }
    }

    private void updateBackupInfo() {
        b.backupInfo.setText(prefs.backupUri().isEmpty()
                ? "Noch keine Sicherung eingerichtet."
                : "✓ Automatische Sicherung aktiv.");
    }

    /** Zeigt nach einer Wiederherstellung die übernommenen Werte an. */
    private void refreshFromPrefs() {
        updateAgentInfo();
        showWishes();
        b.switchBackupKeys.setChecked(prefs.backupKeys());
        showMemory();
        // Auch die Schlüssel neu anzeigen: Sonst schrieb das Verlassen der Einstellungen (saveTextFields) die
        // alten, meist leeren Felder zurück – und die eben wiederhergestellten Schlüssel waren wieder weg.
        b.keyField.setText(prefs.apiKey());
        b.keyLayout.setHelperText(prefs.apiKeyPreview());
        b.elevenKeyField.setText(prefs.elevenKey());
        b.agentTokenField.setText(prefs.agentToken());
        b.githubTokenField.setText(prefs.githubToken());
        b.githubTokenLayout.setHelperText(prefs.githubTokenPreview());
        b.modelField.setText(prefs.rawModel());
        b.elevenVoiceField.setText(prefs.rawElevenVoice());
        b.switchListen.setChecked(prefs.alwaysListen());
        setSlider(b.sliderSensitivity, prefs.sensitivity());
        b.switchEarcon.setChecked(prefs.earcon());
        b.switchBargeIn.setChecked(prefs.bargeIn());
        setSlider(b.sliderBargeIn, prefs.bargeInSensitivity());
        b.switchEchoCall.setChecked(prefs.echoCallMode());
        b.switchGameAuto.setChecked(prefs.gameAuto());
        b.switchGameThink.setChecked(prefs.gameThinkDeep());
        b.switchProactive.setChecked(prefs.proactiveCoach());
        setSlider(b.sliderPause, prefs.pauseMs() / 1000f);
        setSlider(b.sliderRate, prefs.rate());
        setSlider(b.sliderPitch, prefs.pitch());
        b.switchFloatingJ.setChecked(prefs.floatingJ());
        b.switchSubtitles.setChecked(prefs.subtitles());
        b.switchSubtitlesGame.setChecked(prefs.subtitlesInGame());
        b.positionGroup.check(prefs.subtitlesTop() ? R.id.positionTop : R.id.positionBottom);
        b.switchGame.setChecked(prefs.tftMode());
        String active = Voices.resolveEngine(this, prefs);
        for (int i = 0; i < engines.size(); i++) {
            if (engines.get(i).pkg.equals(active)) b.engineDropdown.setText(engines.get(i).label, false);
        }
        loadPreviewEngine(active);
    }

    // ---------------------------------------------------------------- Speichern

    @Override
    protected void onResume() {
        super.onResume();
        // Jarvis kann sich im Hintergrund Neues gemerkt haben.
        showMemory();
        updateOverlayWarning();
        updateUsageAccess(); // zurück von der Systemseite: Freigabe erteilt?
        updateBattery();
        refreshService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveTextFields();
        refreshService();
    }

    @Override
    protected void onDestroy() {
        if (previewTts != null) previewTts.shutdown();
        if (elevenPreview != null) elevenPreview.stop();
        super.onDestroy();
    }

    private void saveTextFields() {
        prefs.setApiKey(text(b.keyField));
        b.keyLayout.setHelperText(prefs.apiKeyPreview());
        String model = text(b.modelField).trim();
        if (!model.isEmpty() && !Prefs.isValidModel(model)) {
            b.modelField.setText("");
            model = "";
            b.modelLayout.setError("Kein gültiger Modellname – Jarvis wählt automatisch.");
        } else {
            b.modelLayout.setError(null);
        }
        prefs.setModel(model);
        String memory = text(b.memoryField);
        if (!memory.equals(shownMemory)) {
            prefs.setMemory(memory);
            shownMemory = prefs.memory();
        }
        prefs.setAgentToken(text(b.agentTokenField));
        prefs.setGithubToken(text(b.githubTokenField));
        b.githubTokenLayout.setHelperText(prefs.githubTokenPreview());
        prefs.setElevenKey(text(b.elevenKeyField));
        prefs.setElevenVoice(text(b.elevenVoiceField));
        Backup.autoSave(this, prefs);
    }

    private void showMemory() {
        shownMemory = prefs.memory();
        b.memoryField.setText(shownMemory);
    }

    /** Läuft Jarvis, übernimmt er die Änderungen sofort. */
    private void refreshService() {
        Backup.autoSave(this, prefs);
        if (JarvisService.isRunning()) {
            startService(new Intent(this, JarvisService.class).setAction(JarvisService.ACTION_REFRESH));
        }
    }

    // ---------------------------------------------------------------- Hilfen

    private static String text(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }

    private void updateMetaInfo() {
        String date = prefs.metaDate();
        if (date.isEmpty()) {
            b.metaInfo.setText("Noch keine Meta recherchiert.");
            return;
        }
        String text = Meta.clean(prefs.metaText()).replaceAll("\\s+", " ").trim();
        if (text.length() > 160) text = text.substring(0, 160) + " …";
        b.metaInfo.setText("Stand " + date + ": " + text);
    }

    /** Recherche sofort starten (z. B. nach einem neuen Patch). */
    private void updateMetaNow() {
        saveTextFields();
        if (prefs.apiKey().isEmpty() || prefs.isGemini()) {
            b.metaInfo.setText("Die Meta-Recherche braucht einen Claude-Schlüssel (sk-ant-…).");
            return;
        }
        b.metaUpdate.setEnabled(false);
        b.metaInfo.setText("Recherchiere die aktuelle Meta … (dauert etwa 20–40 Sekunden)");
        final String today = JarvisService.today();
        new ClaudeClient().sendWithWebSearch(prefs.apiKey(), Meta.MODEL, Meta.system(), Meta.prompt(today),
                Meta.MAX_SEARCHES, new ClaudeClient.Callback() {
                    @Override
                    public void onResult(String text) {
                        if (!Meta.isPlausible(text)) {
                            b.metaUpdate.setEnabled(true);
                            b.metaInfo.setText("✗ Recherche unvollständig – kein Set oder Patch erkennbar. Bitte später noch einmal.");
                            return;
                        }
                        prefs.setMeta(text, today);
                        b.metaUpdate.setEnabled(true);
                        updateMetaInfo();
                    }

                    @Override
                    public void onError(String message) {
                        b.metaUpdate.setEnabled(true);
                        b.metaInfo.setText("✗ " + message);
                    }
                });
    }

    private void showResult(android.widget.TextView view, String message) {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
    }

    /** Setzt einen Schieberegler sicher (auf Raster und Bereich), sonst wirft Material eine Ausnahme. */
    private static void setSlider(Slider s, float value) {
        float from = s.getValueFrom();
        float to = s.getValueTo();
        float step = s.getStepSize();
        float v = Math.max(from, Math.min(to, value));
        if (step > 0) {
            v = from + Math.round((v - from) / step) * step;
            v = Math.round(v * 1000f) / 1000f;
            v = Math.max(from, Math.min(to, v));
        }
        s.setValue(v);
    }

    private static Slider.OnSliderTouchListener onRelease(Runnable r) {
        return new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider slider) { }
            @Override public void onStopTrackingTouch(Slider slider) { r.run(); }
        };
    }

    private String seconds(float v) {
        return String.format(Locale.GERMANY, "%.1f s", v);
    }

    private String decimal(float v) {
        return String.format(Locale.GERMANY, "%.2f", v);
    }

    private String pitchText(float v) {
        return decimal(v) + (v < 0.995f ? " (tiefer)" : v > 1.005f ? " (höher)" : "");
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
