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

        b.chipWake.setOnCheckedChangeListener((chip, checked) -> {
            if (rendering) return;
            prefs.setWakeWord(checked);
            Backup.autoSave(this, prefs);
            sendToService(JarvisService.ACTION_REFRESH);
            render();
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
        render();
    }

    private void readDemo(Intent intent) {
        boolean debuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
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

    @Override
    protected void onStart() {
        super.onStart();
        JarvisService.setUiListener(this);
        render();
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
        ReactorView.Mode mode;
        String label;
        String status;
        String detail;
        if (!running) {
            mode = ReactorView.Mode.OFF;
            label = "STARTEN";
            status = "Inaktiv";
            detail = prefs.apiKey().isEmpty()
                    ? "Trag zuerst in den Einstellungen deinen API-Schlüssel ein."
                    : "Tippe auf den Kern, um Jarvis zu starten.";
        } else {
            switch (s) {
                case LISTENING:
                    mode = ReactorView.Mode.LISTENING;
                    label = "HÖRE ZU";
                    status = "Ich höre zu …";
                    detail = "Sprich einfach. Antippen schickt sofort ab.";
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
                    detail = prefs.wakeWord() ? "Antippen oder „Jarvis“ unterbricht." : "Antippen unterbricht.";
                    break;
                default:
                    mode = ReactorView.Mode.IDLE;
                    label = "SPRECHEN";
                    status = "Bereit";
                    detail = idleDetail();
            }
        }
        b.reactor.setMode(mode, label);
        b.statusText.setText(status);
        b.statusDetail.setText(detail);
        b.statusDot.setBackgroundTintList(ColorStateList.valueOf(
                running ? Palette.color(this, s) : Palette.off(this)));

        b.chipWake.setChecked(prefs.wakeWord());
        b.chipGame.setChecked(prefs.tftMode());
        b.chipScreen.setChecked(JarvisService.isScreenShared());

        b.hint.setVisibility(running ? View.VISIBLE : View.GONE);
        String timing = JarvisService.lastTiming();
        b.hint.setText(timing.isEmpty() || s != JarvisService.State.IDLE
                ? "Tippen: sprechen · Lang drücken: neues Gespräch"
                : "Letzte Antwort: " + timing);
        b.stopButton.setVisibility(running ? View.VISIBLE : View.INVISIBLE);
        if (demo != null) {
            renderExchange("Was soll ich in dieser Runde kaufen?",
                    "Kauf den zweiten Kämpfer und leg ihn auf die Bank. Danach: auf Stufe sechs gehen.");
        } else {
            renderExchange(JarvisService.lastYou(), JarvisService.lastJarvis());
        }
        rendering = false;
    }

    private String idleDetail() {
        if (!prefs.wakeWord()) return "Tippe auf den Kern, um zu sprechen.";
        String problem = JarvisService.wakeProblem();
        if (problem != null) return "Wachwort pausiert: " + problem;
        return "Sag „Jarvis“ – oder tippe auf den Kern.";
    }

    private void renderExchange(String you, String jarvis) {
        boolean hasYou = you != null && !you.isEmpty();
        boolean hasJarvis = jarvis != null && !jarvis.isEmpty();
        b.lastYou.setVisibility(hasYou ? View.VISIBLE : View.GONE);
        b.lastJarvis.setVisibility(hasJarvis ? View.VISIBLE : View.GONE);
        if (hasYou) b.lastYou.setText("Du: " + you);
        if (hasJarvis) b.lastJarvis.setText(jarvis);
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
        renderExchange(you, jarvis);
    }

    @Override
    public void onStatusChanged() {
        render();
    }

    // ---------------------------------------------------------------- Starten

    /** Geht Schritt für Schritt alle nötigen Freigaben durch und startet dann Jarvis. */
    private void proceed() {
        if (prefs.apiKey().isEmpty()) {
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
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        projectionLauncher.launch(mpm.createScreenCaptureIntent());
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
            Snackbar.make(b.getRoot(), "Jarvis läuft ohne Bildschirm. Für Tipps zum Bildschirm neu starten und „Gesamter Bildschirm“ wählen.",
                    Snackbar.LENGTH_LONG).show();
        }
        b.getRoot().postDelayed(this::render, 300);
    }
}
