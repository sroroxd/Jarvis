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
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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
        ReadyReceiver.cancel(this);
        JarvisService.setUiListener(this);
        render();
        checkForUpdate();
    }

    /** Höchstens alle 30 Minuten bei GitHub nachfragen (ohne Anmeldung: 60 Abfragen pro Stunde). */
    private static long lastUpdateCheck;

    /**
     * Liegt auf GitHub eine neuere Version, sagt die App es – mit Knopf zum Herunterladen.
     * Nicht in Debug-Builds: Die Vorschaubilder entstehen aus einem anderen Workflow mit eigener
     * Nummerierung und würden sonst immer einen Hinweis zeigen.
     */
    private void checkForUpdate() {
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

    private void sendToService(String action) {
        if (!JarvisService.isRunning()) return;
        startService(new Intent(this, JarvisService.class).setAction(action));
    }

    // ---------------------------------------------------------------- Anzeige

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
            status = "Inaktiv";
            detail = prefs.brainKey().isEmpty() && prefs.agentReady() && !prefs.tftMode()
                    ? "Agentbetrieb ohne API-Schlüssel · Tippe zum Starten. Antworten können länger dauern."
                    : prefs.brainKey().isEmpty()
                    ? (prefs.useOpenAI() ? "Trag das lokale Kopplungstoken bei Agent in den Einstellungen ein."
                        : "Trag zuerst in den Einstellungen deinen API-Schlüssel ein.")
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
        b.statusDot.setBackgroundTintList(ColorStateList.valueOf(
                running ? Palette.color(this, s) : Palette.off(this)));

        b.chipListen.setChecked(prefs.alwaysListen());
        b.chipGame.setChecked(prefs.tftMode());
        b.chipScreen.setChecked(JarvisService.isScreenShared());

        // Die Dazwischenreden-Diagnose zeigen, sobald es Werte gibt – auch im Dauergespräch und nach dem Beenden.
        // Die „Letzte Antwort“-Zeile mit den Sekundenangaben (Zuhören/KI/Stimme/…) ist entfernt (Wunsch #71):
        // Art kann damit nichts anfangen. Die Messung selbst bleibt – sie steht im Gerätetest-Protokoll der CI.
        String report = JarvisService.bargeInReport();
        StringBuilder hint = new StringBuilder();
        if (running) hint.append(live ? "Dauergespräch · Tippen: beenden" : "Tippen: Dauergespräch · Lang drücken: neues Gespräch");
        if (!report.isEmpty()) hint.append(hint.length() > 0 ? "\n" : "").append("Dazwischenreden: ").append(report);
        // Tagessumme aller Claude-Anfragen (auch Phasen-Wächter und Meta) – Anthropic rechnet in US-Dollar.
        double today = prefs.costToday(JarvisService.today());
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
        if (!AgentRouting.canStart(!prefs.brainKey().isEmpty(), prefs.agentReady(), prefs.tftMode())) {
            Snackbar.make(b.getRoot(), prefs.agentReady() && prefs.tftMode()
                    ? "Für den Agentbetrieb ohne API-Schlüssel bitte zuerst den Spielmodus ausschalten."
                    : prefs.useOpenAI() ? "Bitte zuerst das lokale Kopplungstoken bei Agent eintragen."
                    : "Bitte zuerst den API-Schlüssel eintragen.", Snackbar.LENGTH_LONG)
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
                    android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay());
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
        startForegroundService(svc);
        if (!withScreen) {
            Snackbar.make(b.getRoot(), "Ohne Freigabe sieht Jarvis nichts. Tippe oben auf „Bildschirm“, um sie nachzuholen.",
                    Snackbar.LENGTH_LONG).show();
        }
        b.getRoot().postDelayed(this::render, 300);
    }
}
