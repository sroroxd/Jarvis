package de.art.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Einrichtung: Schlüssel eintragen, Berechtigungen erteilen, Jarvis starten. */
public class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 1;
    private static final int REQ_OVERLAY = 2;
    private static final int REQ_PROJECTION = 3;

    private Prefs prefs;
    private EditText keyField;
    private EditText modelField;
    private Switch tftSwitch;
    private TextView status;
    private boolean askedNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(48), pad, pad);
        root.setBackgroundColor(0xFF0B1D2A);

        TextView title = text("J.A.R.V.I.S.", 30, 0xFF7FE7FF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView subtitle = text("Dein persönlicher Assistent", 15, 0xFFB0C4D0);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        root.addView(space(24));
        root.addView(text("Anthropic-API-Schlüssel", 14, Color.WHITE));
        keyField = new EditText(this);
        keyField.setHint("sk-ant-…");
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setText(prefs.apiKey());
        keyField.setTextColor(Color.WHITE);
        keyField.setHintTextColor(0xFF607D8B);
        root.addView(keyField);

        root.addView(space(8));
        root.addView(text("Modell (leer lassen = Standard)", 14, Color.WHITE));
        modelField = new EditText(this);
        modelField.setHint(Prefs.DEFAULT_MODEL);
        modelField.setSingleLine(true);
        String storedModel = prefs.model();
        modelField.setText(Prefs.DEFAULT_MODEL.equals(storedModel) ? "" : storedModel);
        modelField.setTextColor(Color.WHITE);
        modelField.setHintTextColor(0xFF607D8B);
        root.addView(modelField);

        root.addView(space(8));
        tftSwitch = new Switch(this);
        tftSwitch.setText("TFT-Coach-Modus");
        tftSwitch.setTextColor(Color.WHITE);
        tftSwitch.setChecked(prefs.tftMode());
        root.addView(tftSwitch);
        root.addView(text("Hinweis: Live-Tipps während einer Partie verstoßen gegen Riots Regeln – Nutzung auf eigenes Risiko.", 12, 0xFF90A4AE));

        root.addView(space(20));
        Button start = button("Jarvis starten");
        start.setOnClickListener(v -> {
            save();
            proceed();
        });
        root.addView(start);

        Button stop = button("Jarvis beenden");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, JarvisService.class));
            setStatus("Jarvis ist beendet.");
        });
        root.addView(stop);

        root.addView(space(12));
        status = text("", 14, 0xFF7FE7FF);
        root.addView(status);

        root.addView(space(24));
        root.addView(text("So nutzt du Jarvis", 16, Color.WHITE));
        root.addView(text(
                "• Tippe auf das schwebende J und sprich. Jarvis sieht dabei deinen Bildschirm.\n"
                        + "• Farbe des J: blau = bereit, rot = hört zu, orange = denkt, grün = spricht.\n"
                        + "• Während Jarvis spricht: aufs J tippen = unterbrechen und neu fragen.\n"
                        + "• J lange drücken = neues Gespräch. J lässt sich frei verschieben.\n"
                        + "• Seitentaste: Einstellungen → Erweiterte Funktionen → Seitentaste → Zweimal drücken → App öffnen → „Jarvis sprechen“.\n"
                        + "• Bei der Bildschirmfreigabe „Gesamter Bildschirm“ wählen.",
                14, 0xFFB0C4D0));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B1D2A);
        scroll.addView(root);
        setContentView(scroll);

        if (JarvisService.isRunning()) setStatus("Jarvis läuft.");
    }

    private void save() {
        prefs.setApiKey(keyField.getText().toString());
        prefs.setModel(modelField.getText().toString());
        prefs.setTftMode(tftSwitch.isChecked());
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    /** Geht Schritt für Schritt alle nötigen Freigaben durch und startet dann Jarvis. */
    private void proceed() {
        if (prefs.apiKey().isEmpty()) {
            setStatus("Bitte zuerst den API-Schlüssel eintragen.");
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
        if (!missing.isEmpty()) {
            askedNotifications = true;
            requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            setStatus("Bitte „Über anderen Apps anzeigen“ für Jarvis erlauben und zurückkehren.");
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
            return;
        }
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PERMISSIONS) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            proceed();
        } else {
            setStatus("Ohne Mikrofon kann Jarvis dich nicht hören. Bitte in den App-Einstellungen erlauben.");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) proceed();
            else setStatus("Ohne „Über anderen Apps anzeigen“ kann das J nicht erscheinen.");
        } else if (requestCode == REQ_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent svc = new Intent(this, JarvisService.class)
                        .setAction(JarvisService.ACTION_START)
                        .putExtra(JarvisService.EXTRA_CODE, resultCode)
                        .putExtra(JarvisService.EXTRA_DATA, data);
                startForegroundService(svc);
                setStatus("Jarvis läuft. Tippe auf das J, um zu sprechen.");
                moveTaskToBack(true);
            } else {
                setStatus("Ohne Bildschirmfreigabe kann Jarvis nichts sehen. Bitte nochmal starten und erlauben.");
            }
        }
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView space(int heightDp) {
        TextView t = new TextView(this);
        t.setHeight(dp(heightDp));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
