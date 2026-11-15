package de.art.jarvis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.art.jarvis.databinding.ActivityMainBinding;

/** Startbildschirm: Status und der große Sprechen-Knopf. Alles Weitere liegt in den Einstellungen. */
public class MainActivity extends AppCompatActivity implements JarvisService.UiListener {
    private ActivityMainBinding b;
    private Prefs prefs;
    private boolean askedNotifications;
    private boolean askedCalendar;
    private boolean askedOverlay;
    private boolean rendering;
    private boolean compactWindow;
    private int compactReactorHeight;
    private ViewGroup normalChipParent;
    private String openAiDetail = "";
    private AlertDialog openAiStatusDialog;
    private boolean resumed;
    private Intent pendingStart;
    /** Nur in der Debug-Variante: fester Zustand für die automatischen Vorschaubilder. */
    private JarvisService.State demo;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);
    private final ActivityResultLauncher<Intent> overlayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> proceed());
    private final ActivityResultLauncher<Intent> projectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    r -> startJarvis(r.getResultCode(), r.getData()));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = new Prefs(this);
        readDemo(getIntent());
        readTestHooks(getIntent());
        // Build-Nummer sichtbar – so sieht man auf jedem Bildschirmfoto, welche Version läuft.
        b.subtitle.setText("Dein persönlicher Assistent · " + (BuildConfig.TEST_APP ? "Test-Build " : "Build ")
                + BuildConfig.VERSION_CODE);

        b.settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        if (BuildConfig.OPENAI_TEST) {
            b.mainOpenAiImage.setVisibility(View.VISIBLE);
            b.mainOpenAiImage.setOnClickListener(v -> sendToService(JarvisService.ACTION_OPENAI_IMAGE));
            normalChipParent = (ViewGroup) b.mainStatusChips.getParent();
            b.statusDetail.setOnClickListener(v -> {
                if (compactWindow) {
                    if (openAiStatusDialog != null) openAiStatusDialog.dismiss();
                    openAiStatusDialog = new AlertDialog.Builder(this)
                            .setTitle(b.statusText.getText()).setMessage(openAiDetail)
                            .setPositiveButton("Schließen", null).show();
                }
            });
            b.statusDetail.setClickable(false);
            // Die tatsächlich verfügbare Höhe zählt, auch nach dem Verschieben des Split-Screen-Trenners.
            b.getRoot().addOnLayoutChangeListener((v, l, t, r, bottom, ol, ot, or, ob) ->
                    adaptOpenAiWindow(bottom - t));
        }

        b.reactor.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            if (JarvisService.isRunning()) sendToService(JarvisService.ACTION_LISTEN);
            else proceed();
        });
        b.reactor.setOnLongClickListener(v -> {
            if (!JarvisService.isRunning()) return false;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            sendToService(JarvisService.ACTION_RESET);
            return true;
        });

        b.stopButton.setOnClickListener(v -> {
            stopService(new Intent(this, JarvisService.class));
            b.getRoot().postDelayed(this::render, 150);
        });

        b.chipListen.setOnCheckedChangeListener((chip, checked) -> {
            if (rendering) return;
            prefs.setAlwaysListen(checked);
            Backup.autoSave(this, prefs);
            sendToService(JarvisService.ACTION_REFRESH);
            render();
        });
        b.chipScreen.setOnClickListener(v -> {
            render(); // Haken zeigt immer den echten Zustand, nicht den Klick
            if (!JarvisService.isRunning()) {
                proceed(); // Start fragt die Freigabe ohnehin ab
            } else if (JarvisService.isScreenShared()) {
                Snackbar.make(b.getRoot(), "Der Bildschirm wird schon geteilt – Jarvis sieht mit.",
                        Snackbar.LENGTH_SHORT).show();
            } else {
                projectionLauncher.launch(screenCaptureIntent());
            }
        });
        b.chipGame.setOnCheckedChangeListener((chip, checked) -> {
            if (rendering) return;
            prefs.setTftMode(checked);
            Backup.autoSave(this, prefs);
            sendToService(JarvisService.ACTION_REFRESH);
            if (checked) {
                Snackbar.make(b.getRoot(), "Spielmodus: nichts verdeckt das Spiel, Jarvis antwortet nur per Stimme.",
                        Snackbar.LENGTH_LONG).show();
            }
            render();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readDemo(intent);
        readTestHooks(intent);
        render();
    }

    private void readDemo(Intent intent) {
        boolean debuggable = BuildConfig.DEBUG;
        String d = intent == null ? null : intent.getStringExtra("demo");
        demo = null;
        if (debuggable && d != null) {
            try {
                demo = JarvisService.State.valueOf(d);
            } catch (IllegalArgumentException ignored) {
                // unbekannter Zustand
            }
        }
    }

    /**
     * Nur Debug-Builds, für den Gesprächstest in der CI (siehe .github/scripts/geraetetest.sh):
     * „testkey“ trägt den API-Schlüssel ein, „teststart“ startet Jarvis ohne Bildschirmfreigabe,
     * „testfrage“ stellt eine Frage, als wäre sie gesprochen (mit „testoffen“ wie bei offenem Mikrofon nach einer
     * Gesprächspause), „testwaechter“ fragt einmal den Phasen-Wächter des
     * proaktiven Coachings mit einem Testbild. In der Handy-Version (Release) wirkungslos.
     */
    private void readTestHooks(Intent intent) {
        boolean debuggable = BuildConfig.DEBUG;
        if (!debuggable || intent == null) return;
        String key = intent.getStringExtra("testkey");
        if (key != null && !key.trim().isEmpty()) {
            prefs.setApiKey(key.trim());
            // Gesprächstest: Der Emulator hat kein Mikrofon – „Immer zuhören“ würde nur Fehler der Spracherkennung
            // zwischen die Testfragen streuen. Die Fragen kommen über „testfrage“.
            prefs.setAlwaysListen(false);
        }
        // „testpocketstimme“: Jarvis spricht mit der Offline-Stimme (Modell muss schon im App-Ordner liegen).
        if (intent.getBooleanExtra("testpocketstimme", false)) prefs.setPocketVoice(true);
        if (intent.getBooleanExtra("teststart", false)) startJarvis(RESULT_CANCELED, null);
        String frage = intent.getStringExtra("testfrage");
        if (frage != null && !frage.trim().isEmpty()) {
            startService(new Intent(this, JarvisService.class)
                    .setAction(JarvisService.ACTION_TEST_ASK)
                    .putExtra(JarvisService.EXTRA_TEXT, frage.trim())
                    // „testoffen“: wie bei offenem Mikrofon nach einer Gesprächspause – der Filter vor Claude fragt mit.
                    .putExtra("offen", intent.getBooleanExtra("testoffen", false)));
        }
        if (intent.getBooleanExtra("testpocket", false)) { // Offline-Stimme, siehe JarvisService.testPocket
            startService(new Intent(this, JarvisService.class).setAction(JarvisService.ACTION_TEST_POCKET));
        }
        if (intent.getBooleanExtra("testwaechter", false)) {
            startService(new Intent(this, JarvisService.class).setAction(JarvisService.ACTION_TEST_WATCH));
        }
        if (intent.getBooleanExtra("testwaechterbilder", false)) { // Arts echte TFT-Bilder, siehe geraetetest.sh
            startService(new Intent(this, JarvisService.class).setAction(JarvisService.ACTION_TEST_WATCH)
                    .putExtra("bilder", true));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        JarvisService.setUiListener(this);
        render();
        checkForUpdate();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        resumed = true;
        startPendingJarvis();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    /** Höchstens alle 30 Minuten bei GitHub nachfragen (ohne Anmeldung: 60 Abfragen pro Stunde). */
    private static long lastUpdateCheck;

    /**
     * Liegt auf GitHub eine neuere Version, sagt die App es – mit Knopf zum Herunterladen.
     * Nicht in Debug-Builds: Die Vorschaubilder entstehen aus einem anderen Workflow mit eigener
     * Nummerierung und würden sonst immer einen Hinweis zeigen.
     */
    private void checkForUpdate() {
        // Eigene Paket-ID und temporäre Testsignatur: keine APK anderer Testzweige als Update anbieten.
        if (BuildConfig.OPENAI_TEST) return;
        boolean debuggable = BuildConfig.DEBUG;
        if (debuggable || demo != null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastUpdateCheck != 0 && now - lastUpdateCheck < 30 * 60 * 1000L) return;
        lastUpdateCheck = now;
        new Thread(() -> {
            UpdateCheck.Result r = UpdateCheck.fetchNewer(BuildConfig.TEST_APP, BuildConfig.BRANCH, BuildConfig.VERSION_CODE);
            if (r == null) return;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Snackbar.make(b.getRoot(), "Neuere " + (BuildConfig.TEST_APP ? "Test-Version" : "Version")
                                + " liegt bereit: Build " + r.build + " (installiert: " + BuildConfig.VERSION_CODE + ").",
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction("Installieren", v -> installUpdate(r))
                        .show();
            });
        }, "Jarvis-UpdateCheck").start();
    }

    /** Neue Version direkt einspielen (ohne Browser, ohne Datei im Download-Ordner). */
    private void installUpdate(UpdateCheck.Result r) {
        if (!r.url.endsWith(".apk")) { // kein APK am Release – dann eben die Seite öffnen
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.url)));
            } catch (Exception ignored) {
                // kein Browser – sehr unwahrscheinlich
            }
            return;
        }
        Snackbar bar = Snackbar.make(b.getRoot(), "Build " + r.build + " wird geladen …", Snackbar.LENGTH_INDEFINITE);
        boolean started = AppUpdater.install(this, r.apiUrl, r.url, r.build, new AppUpdater.Progress() {
            @Override
            public void status(String text) {
                if (!isFinishing() && !isDestroyed()) bar.setText(text);
            }

            @Override
            public void failed(String text) {
                if (isFinishing() || isDestroyed()) return;
                // Vorher blieb nur der Fehlertext stehen – einen neuen Versuch gab es erst nach 30 Minuten.
                bar.setText(text);
                bar.setAction("Nochmal", v -> installUpdate(r));
            }
        });
        if (started) {
            bar.show();
        } else {
            lastUpdateCheck = 0; // nach dem Erlauben beim Zurückkehren gleich wieder anbieten
            Toast.makeText(this, "Bitte „Apps aus dieser Quelle zulassen“ einschalten und zurückkehren.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStop() {
        JarvisService.setUiListener(null);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (openAiStatusDialog != null) {
            openAiStatusDialog.dismiss();
            openAiStatusDialog = null;
        }
        super.onDestroy();
    }

    private void sendToService(String action) {
        if (!JarvisService.isRunning()) return;
        startService(new Intent(this, JarvisService.class).setAction(action));
    }

    // ---------------------------------------------------------------- Anzeige

    /** Kleine Fenster: Kern und Stop bleiben fest, Zusatzbedienung und Verlauf sind scrollbar. */
    private void adaptOpenAiWindow(int height) {
        height -= b.getRoot().getPaddingTop() + b.getRoot().getPaddingBottom();
        if (height <= 0) return;
        boolean compact = height < dp(600);
        int reactorHeight = Math.max(dp(96), Math.min(dp(160), height / 3));
        if (compact == compactWindow && (!compact || reactorHeight == compactReactorHeight)) return;
        boolean changedMode = compact != compactWindow;
        compactWindow = compact;
        compactReactorHeight = reactorHeight;
        LinearLayout root = (LinearLayout) b.getRoot();
        if (changedMode) {
            ((ViewGroup) b.mainHeader.getParent()).removeView(b.mainHeader);
            ((ViewGroup) b.mainStatusChips.getParent()).removeView(b.mainStatusChips);
            ((ViewGroup) b.mainOpenAiImage.getParent()).removeView(b.mainOpenAiImage);
            ((ViewGroup) b.hint.getParent()).removeView(b.hint);
            if (compact) {
                b.mainTranscriptContent.addView(b.mainHeader, 0);
                b.mainTranscriptContent.addView(b.mainStatusChips, 1);
                b.mainTranscriptContent.addView(b.mainOpenAiImage, 2);
                b.mainTranscriptContent.addView(b.hint, 3);
            } else {
                root.addView(b.mainHeader, 0);
                normalChipParent.addView(b.mainStatusChips);
                root.addView(b.mainOpenAiImage, root.indexOfChild(b.mainReactorContainer));
                root.addView(b.hint, root.indexOfChild(b.logScroll));
            }
            b.subtitle.setVisibility(compact ? View.GONE : View.VISIBLE);
            b.mainHeader.setPaddingRelative(compact ? 0 : dp(24), compact ? 0 : dp(12), compact ? 0 : dp(8), 0);
            b.mainStatusCard.setContentPadding(dp(compact ? 12 : 18), compact ? 0 : dp(18),
                    dp(compact ? 12 : 18), compact ? 0 : dp(18));
            LinearLayout.LayoutParams card = (LinearLayout.LayoutParams) b.mainStatusCard.getLayoutParams();
            card.topMargin = compact ? 0 : dp(16);
            b.mainStatusCard.setLayoutParams(card);
            b.statusDetail.setMaxLines(compact ? 1 : Integer.MAX_VALUE);
            b.statusDetail.setEllipsize(compact ? TextUtils.TruncateAt.END : null);
            b.statusDetail.setClickable(compact);
            b.statusDetail.setFocusable(compact);
            LinearLayout.LayoutParams stop = (LinearLayout.LayoutParams) b.stopButton.getLayoutParams();
            stop.topMargin = compact ? 0 : dp(8);
            stop.bottomMargin = compact ? 0 : dp(12);
            b.stopButton.setLayoutParams(stop);
            b.mainReactorContainer.setPadding(dp(24), compact ? 0 : dp(8), dp(24), compact ? 0 : dp(8));
            b.logScroll.setPadding(dp(32), compact ? 0 : dp(6), dp(32), 0);
            showOpenAiDetail();
        }
        LinearLayout.LayoutParams reactor = (LinearLayout.LayoutParams) b.mainReactorContainer.getLayoutParams();
        reactor.height = compact ? reactorHeight : 0;
        reactor.weight = compact ? 0 : 1;
        b.mainReactorContainer.setLayoutParams(reactor);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showOpenAiDetail() {
        b.statusDetail.setText(compactWindow
                ? prefs.openAiSelected() ? "Kostenpflichtig · Status öffnen" : "Status öffnen"
                : openAiDetail);
        b.statusDetail.setContentDescription(openAiDetail
                + (compactWindow ? " Vollständigen Status durch Antippen öffnen." : ""));
    }

    private void render() {
        rendering = true;
        boolean running = JarvisService.isRunning() || demo != null;
        JarvisService.State s = demo != null ? demo : JarvisService.currentState();
        boolean live = demo == null && JarvisService.isContinuous();
        ReactorView.Mode mode;
        String label;
        String status;
        String detail;
        if (!running) {
            mode = ReactorView.Mode.OFF;
            label = "STARTEN";
            status = JarvisService.startFailure().isEmpty() ? "Inaktiv" : "Start nicht möglich";
            detail = !JarvisService.startFailure().isEmpty() ? JarvisService.startFailure()
                    : !prefs.selectedProviderReady()
                    ? "Trag zuerst in den Einstellungen deinen API-Schlüssel ein."
                    : "Tippe auf den Kern, um Jarvis zu starten.";
        } else {
            switch (s) {
                case LISTENING:
                    mode = ReactorView.Mode.LISTENING;
                    label = "HÖRE ZU";
                    status = live ? "Dauergespräch · ich höre zu" : "Ich höre zu …";
                    detail = live
                            ? (JarvisService.isScreenShared() ? "Sprich einfach – jede Frage bekommt ein frisches Bild. Antippen pausiert."
                                    : "Sprich einfach, ich höre durchgehend zu. Antippen pausiert.")
                            : "Sprich einfach. Antippen schickt sofort ab.";
                    break;
                case THINKING:
                    mode = ReactorView.Mode.THINKING;
                    label = "DENKE …";
                    status = "Denke nach …";
                    detail = JarvisService.isScreenShared() ? "Mit Blick auf deinen Bildschirm." : "Einen Moment.";
                    break;
                case SPEAKING:
                    mode = ReactorView.Mode.SPEAKING;
                    label = "SPRICHT";
                    status = "Spreche …";
                    if (live && prefs.bargeIn()) {
                        // Im Dauergespräch unterbrechen Losreden und „Jarvis“ (Dazwischenreden-Mikrofon).
                        detail = "Losreden oder „Jarvis“ unterbricht · Antippen pausiert.";
                    } else {
                        detail = "Antippen unterbricht.";
                    }
                    break;
                default:
                    mode = ReactorView.Mode.IDLE;
                    label = "SPRECHEN";
                    status = "Bereit";
                    // Im Dauergespräch ist „Bereit“ nur der kurze Moment zwischen zwei Mal Zuhören.
                    detail = live ? "Gleich höre ich wieder zu." : idleDetail();
            }
        }
        b.reactor.setMode(mode, label);
        b.statusText.setText(status);
        b.statusDetail.setText(detail);
        if (BuildConfig.OPENAI_TEST) {
            // Ein Status statt zweier konkurrierender Infoblöcke. Vor dem Start zählt die aktuelle
            // Konfiguration, nicht der statische Bericht einer noch nicht geöffneten Sitzung.
            if (!running && !JarvisService.startFailure().isEmpty()) {
                b.statusDetail.setText(JarvisService.startFailure());
            } else if (prefs.openAiSelected()) {
                b.statusDetail.setText(running ? JarvisService.openAiStatus()
                        : prefs.selectedProviderReady()
                                ? "OpenAI Realtime bereit. Kostenpflichtig. Tippe auf den Kern, um die Sitzung zu starten."
                                : "OpenAI-Schlüssel fehlt. Bitte in den Einstellungen eintragen.");
            } else if (!running) {
                b.statusDetail.setText("OpenAI-Test verfügbar: In den Einstellungen OpenAI Realtime auswählen. Kein automatischer Providerwechsel.");
            }
            b.mainOpenAiImage.setEnabled(prefs.openAiSelected() && running);
            b.chipListen.setEnabled(!prefs.openAiSelected());
            if (prefs.openAiSelected() && running && s == JarvisService.State.IDLE) {
                b.statusText.setText("OpenAI-Sitzung beendet");
                b.reactor.setMode(ReactorView.Mode.IDLE, "NEUSTART");
            }
            openAiDetail = b.statusDetail.getText().toString();
            showOpenAiDetail();
        }
        b.statusDot.setBackgroundTintList(ColorStateList.valueOf(
                running ? Palette.color(this, s) : Palette.off(this)));

        b.chipListen.setChecked(prefs.alwaysListen());
        b.chipGame.setChecked(prefs.tftMode());
        b.chipScreen.setChecked(JarvisService.isScreenShared());

        // Die Dazwischenreden-Diagnose zeigen, sobald es Werte gibt – auch im Dauergespräch und nach dem Beenden.
        // Die „Letzte Antwort“-Zeile mit den Sekundenangaben (Zuhören/KI/Stimme/…) ist entfernt (Wunsch #71):
        // Art kann damit nichts anfangen. Die Messung selbst bleibt – sie steht im Gerätetest-Protokoll der CI.
        String report = prefs.openAiSelected() ? "" : JarvisService.bargeInReport();
        StringBuilder hint = new StringBuilder();
        if (running) hint.append(prefs.openAiSelected()
                ? "Tippen: pausieren oder neu starten · Lang drücken: Sitzung beenden"
                : live ? "Dauergespräch · Tippen: beenden" : "Tippen: Dauergespräch · Lang drücken: neues Gespräch");
        if (!report.isEmpty()) hint.append(hint.length() > 0 ? "\n" : "").append("Dazwischenreden: ").append(report);
        // Tagessumme aller Claude-Anfragen (auch Phasen-Wächter und Meta) – Anthropic rechnet in US-Dollar.
        double today = prefs.openAiSelected() ? 0 : prefs.costToday(JarvisService.today());
        if (today > 0) {
            String day = JarvisService.today();
            hint.append(hint.length() > 0 ? "\n" : "").append("Heute bisher: ").append(Costs.cents(today))
                    .append(" (US-Cent)");
            // Wofür – damit ein teurer Tag erklärbar ist (8. Nov.: 875 ct ohne Aufschlüsselung).
            String parts = Costs.breakdown(prefs.costTodayByKind(day));
            if (!parts.isEmpty()) hint.append("\n").append(parts);
            if (prefs.overCostLimit(day)) hint.append("\nTagesgrenze erreicht: Antwort nur auf „Jarvis“");
        }
        b.hint.setVisibility(hint.length() > 0 ? View.VISIBLE : View.GONE);
        b.hint.setText(hint);
        b.stopButton.setVisibility(running ? View.VISIBLE : View.INVISIBLE);
        if (demo != null) {
            ChatLog d = new ChatLog();
            d.art("Wie stehe ich gerade?");
            d.jarvis("Vierzig Gold, Stufe sechs, dein Brett ist stabil. Danach: Zinsen halten.");
            d.art("Was soll ich in dieser Runde kaufen?");
            d.jarvis("Kauf den zweiten Kämpfer und leg ihn auf die Bank. Danach: auf Stufe sechs gehen.");
            renderLog(d.snapshot());
        } else {
            renderLog(JarvisService.log());
        }
        rendering = false;
    }

    private String idleDetail() {
        if (prefs.alwaysListen()) return "Pausiert – tippe auf den Kern, dann höre ich wieder zu.";
        return "Tippe auf den Kern – ich höre dann durchgehend zu.";
    }

    /** Bisher gezeichnete Einträge – so wird bei jedem Zwischenergebnis nur der letzte erneuert. */
    private int renderedEntries;
    private ChatLog.Entry firstRendered;

    /**
     * Zeichnet den Gesprächsverlauf. Neue Einträge werden angehängt, der letzte wird
     * aktualisiert (wachsende Texte), und nur wenn vorne etwas herausgefallen ist oder das
     * Gespräch neu beginnt, wird alles neu aufgebaut. Steht Art beim Lesen weiter oben,
     * springt die Ansicht nicht nach unten.
     */
    private void renderLog(List<ChatLog.Entry> entries) {
        android.widget.LinearLayout list = b.logList;
        boolean atBottom = !b.logScroll.canScrollVertically(1);
        boolean shifted = renderedEntries > 1 && !entries.isEmpty() && entries.get(0) != firstRendered;
        if (entries.size() < renderedEntries || shifted || list.getChildCount() != renderedEntries) {
            list.removeAllViews();
            renderedEntries = 0;
        }
        if (renderedEntries > 0) {
            bindEntry((android.widget.TextView) list.getChildAt(renderedEntries - 1), entries.get(renderedEntries - 1));
        }
        android.view.LayoutInflater inflater = getLayoutInflater();
        for (int i = renderedEntries; i < entries.size(); i++) {
            ChatLog.Entry e = entries.get(i);
            android.widget.TextView tv = (android.widget.TextView) inflater.inflate(
                    e.art ? R.layout.item_log_you : R.layout.item_log_jarvis, list, false);
            bindEntry(tv, e);
            list.addView(tv);
        }
        renderedEntries = entries.size();
        firstRendered = entries.isEmpty() ? null : entries.get(0);
        if (atBottom) b.logScroll.post(() -> b.logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private static void bindEntry(android.widget.TextView tv, ChatLog.Entry e) {
        tv.setText(e.art ? "Sie: " + e.text : e.text); // Wunsch #45: „Sie“ statt „Du“, wie Jarvis Art anspricht
    }

    @Override
    public void onStateChanged(JarvisService.State state) {
        render();
    }

    @Override
    public void onLevel(float level) {
        b.reactor.setLevel(level);
    }

    @Override
    public void onExchange(String you, String jarvis) {
        renderLog(JarvisService.log());
    }

    @Override
    public void onStatusChanged() {
        render();
    }

    // ---------------------------------------------------------------- Starten

    /** Geht Schritt für Schritt alle nötigen Freigaben durch und startet dann Jarvis. */
    private void proceed() {
        if (!prefs.selectedProviderReady()) {
            Snackbar.make(b.getRoot(), "Bitte zuerst den API-Schlüssel eintragen.", Snackbar.LENGTH_LONG)
                    .setAction("Einstellungen", v -> startActivity(new Intent(this, SettingsActivity.class)))
                    .show();
            return;
        }
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        // Kalender (Termine vorlesen und eintragen) – einmal fragen; ohne ihn läuft Jarvis trotzdem.
        if (!askedCalendar && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.READ_CALENDAR);
            missing.add(Manifest.permission.WRITE_CALENDAR);
        }
        if (!missing.isEmpty()) {
            askedNotifications = true;
            askedCalendar = true;
            permissionLauncher.launch(missing.toArray(new String[0]));
            return;
        }
        // „Über anderen Apps anzeigen“ nur, wenn J oder Untertitel eingeschaltet sind – und nur einmal fragen.
        boolean needsOverlay = prefs.floatingJ() || prefs.subtitles() || prefs.subtitlesInGame();
        if (needsOverlay && !askedOverlay && !Settings.canDrawOverlays(this)) {
            askedOverlay = true;
            Toast.makeText(this, "Für Untertitel bitte „Über anderen Apps anzeigen“ erlauben.", Toast.LENGTH_LONG).show();
            overlayLauncher.launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        projectionLauncher.launch(screenCaptureIntent());
    }

    /**
     * Ab Android 14 bietet der Freigabe-Dialog „Eine App teilen“ als Vorauswahl an – dann sieht
     * Jarvis nur diese eine App. Mit dieser Einstellung fragt Android nur nach dem ganzen Bildschirm.
     */
    private Intent screenCaptureIntent() {
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        if (Build.VERSION.SDK_INT >= 34) {
            return mpm.createScreenCaptureIntent(
                    android.media.projection.MediaProjectionConfig.createConfigForUserChoice());
        }
        return mpm.createScreenCaptureIntent();
    }

    private void onPermissions(Map<String, Boolean> result) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            proceed();
        } else {
            Snackbar.make(b.getRoot(), "Ohne Mikrofon kann Jarvis dich nicht hören. Bitte in den App-Einstellungen erlauben.",
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void startJarvis(int resultCode, Intent data) {
        Intent svc = new Intent(this, JarvisService.class).setAction(JarvisService.ACTION_START);
        boolean withScreen = resultCode == RESULT_OK && data != null;
        if (withScreen) {
            svc.putExtra(JarvisService.EXTRA_CODE, resultCode).putExtra(JarvisService.EXTRA_DATA, data);
        }
        // ActivityResult kann vor Resume eintreffen. While-in-use-Mikrofon erst aus der
        // wieder sichtbaren Activity starten; Android entscheidet weiterhin endgültig.
        pendingStart = svc;
        startPendingJarvis();
    }

    private void startPendingJarvis() {
        if (!resumed || pendingStart == null || isFinishing() || isDestroyed()) return;
        Intent svc = pendingStart;
        pendingStart = null;
        JarvisService.clearStartFailure();
        String failure = ForegroundStart.attempt(
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                () -> startForegroundService(svc));
        if (failure != null) {
            JarvisService.reportStartFailure(failure);
            Snackbar.make(b.getRoot(), failure, Snackbar.LENGTH_LONG).show();
            render();
            return;
        }
        if (!svc.hasExtra(JarvisService.EXTRA_DATA)) {
            Snackbar.make(b.getRoot(), "Ohne Freigabe sieht Jarvis nichts. Tippe oben auf „Bildschirm“, um sie nachzuholen.",
                    Snackbar.LENGTH_LONG).show();
        }
        b.getRoot().postDelayed(this::render, 300);
    }
}
