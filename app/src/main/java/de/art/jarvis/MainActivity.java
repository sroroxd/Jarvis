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
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Einrichtung: Schlüssel eintragen, Berechtigungen erteilen, Jarvis starten. */
public class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 1;
    private static final int REQ_OVERLAY = 2;
    private static final int REQ_PROJECTION = 3;
    private static final int REQ_BACKUP_CREATE = 4;
    private static final int REQ_BACKUP_OPEN = 5;

    private Prefs prefs;
    private EditText keyField;
    private TextView keyInfo;
    private EditText modelField;
    private Switch tftSwitch;
    private TextView status;
    private boolean askedNotifications;
    private EditText memoryField;
    private TextView backupInfo;
    private EditText elevenKeyField;
    private EditText elevenVoiceField;
    private ElevenTts elevenPreview;
    private Spinner voiceSpinner;
    private Spinner engineSpinner;
    private List<Voices.Engine> engines;
    private String previewEngine;
    private SeekBar pauseBar;
    private TextView pauseLabel;
    private SeekBar pitchBar;
    private SeekBar rateBar;
    private TextView pitchLabel;
    private TextView rateLabel;
    private TextToSpeech previewTts;
    private boolean previewReady;
    private List<Voice> voices;

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
        root.addView(text("API-Schlüssel (Gemini: AIza…, Claude: sk-ant-…)", 14, Color.WHITE));
        keyField = new EditText(this);
        keyField.setHint("Schlüssel einfügen");
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setText(prefs.apiKey());
        keyField.setTextColor(Color.WHITE);
        keyField.setHintTextColor(0xFF607D8B);
        root.addView(keyField);
        keyInfo = text(prefs.apiKeyPreview(), 12, 0xFF90A4AE);
        root.addView(keyInfo);
        Button testKey = button("Schlüssel testen");
        testKey.setOnClickListener(v -> testKey());
        root.addView(testKey);

        root.addView(space(8));
        root.addView(text("Modell (leer lassen = Standard)", 14, Color.WHITE));
        modelField = new EditText(this);
        modelField.setHint("automatisch");
        modelField.setSingleLine(true);
        modelField.setText(prefs.rawModel());
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
        root.addView(text("Gedächtnis", 16, Color.WHITE));
        root.addView(text("Was Jarvis sich dauerhaft merkt. Sag ihm „Merk dir …“ – oder bearbeite es hier direkt.", 12, 0xFF90A4AE));
        memoryField = new EditText(this);
        memoryField.setHint("Noch nichts gemerkt");
        memoryField.setMinLines(3);
        memoryField.setTextSize(14);
        memoryField.setTextColor(Color.WHITE);
        memoryField.setHintTextColor(0xFF607D8B);
        root.addView(memoryField);

        root.addView(space(20));
        root.addView(text("Sicherung", 16, Color.WHITE));
        root.addView(text("Speichert Gedächtnis und Einstellungen in eine Datei auf deinem Handy (z. B. „Downloads“). "
                + "Sie wird nach jeder Änderung automatisch aktualisiert und übersteht auch eine Deinstallation. "
                + "Der API-Schlüssel wird aus Sicherheitsgründen nicht mitgespeichert.", 12, 0xFF90A4AE));
        backupInfo = text("", 13, 0xFF7FE7FF);
        root.addView(backupInfo);
        updateBackupInfo();
        Button backupCreate = button("Sicherung einrichten");
        backupCreate.setOnClickListener(v -> {
            save();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, Backup.FILE_NAME);
            startActivityForResult(i, REQ_BACKUP_CREATE);
        });
        root.addView(backupCreate);
        Button backupOpen = button("Sicherung wiederherstellen");
        backupOpen.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*");
            startActivityForResult(i, REQ_BACKUP_OPEN);
        });
        root.addView(backupOpen);

        root.addView(space(20));
        buildVoiceSection(root);

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
                "• Tippe auf das schwebende J und sprich. Du kannst Pausen machen – Jarvis schickt erst ab, wenn du fertig bist. Sofort abschicken: nochmal aufs J tippen.\n"
                        + "• Farbe des J: blau = bereit, rot = hört zu, orange = denkt, grün = spricht.\n"
                        + "• Während Jarvis spricht: aufs J tippen = unterbrechen und neu fragen.\n"
                        + "• J lange drücken = neues Gespräch. J lässt sich frei verschieben.\n"
                        + "• „Merk dir …“ speichert dauerhaft, „Vergiss …“ löscht wieder.\n"
                        + "• Seitentaste: Einstellungen → Erweiterte Funktionen → Seitentaste → Zweimal drücken → App öffnen → „Jarvis sprechen“.\n"
                        + "• Bei der Bildschirmfreigabe „Gesamter Bildschirm“ wählen.",
                14, 0xFFB0C4D0));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B1D2A);
        scroll.addView(root);
        setContentView(scroll);

        if (JarvisService.isRunning()) setStatus("Jarvis läuft.");
    }

    private void buildVoiceSection(LinearLayout root) {
        root.addView(text("Stimme", 16, Color.WHITE));
        root.addView(text("Standard: mit ElevenLabs-Schlüssel „Daniel“ (unten). Sonst offline über SherpaTTS: "
                + "Thorsten → Miro → Alan, Tempo 0,9. Deutsche Stimmen vorher in SherpaTTS herunterladen. "
                + "Eigene Auswahl hat immer Vorrang – „Probe hören“ zum Vergleichen.", 12, 0xFF90A4AE));

        root.addView(text("Sprachausgabe-Engine", 14, Color.WHITE));
        engineSpinner = new Spinner(this);
        root.addView(engineSpinner);
        engines = Voices.engines(this);
        List<String> engineLabels = new ArrayList<>();
        String activeEngine = Voices.resolveEngine(this, prefs);
        int engineIndex = 0;
        for (int i = 0; i < engines.size(); i++) {
            engineLabels.add(engines.get(i).label);
            if (engines.get(i).pkg.equals(activeEngine)) engineIndex = i;
        }
        if (engineLabels.isEmpty()) engineLabels.add("Systemstandard");
        engineSpinner.setAdapter(adapter(engineLabels));
        engineSpinner.setSelection(engineIndex);
        engineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position >= engines.size()) return;
                String pkg = engines.get(position).pkg;
                if (pkg.equals(previewEngine)) return; // keine Änderung (z. B. beim ersten Anzeigen)
                prefs.setEngine(pkg);
                prefs.setVoiceName(""); // neue Engine → wieder automatisch wählen
                loadPreviewEngine(pkg);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        root.addView(text("Stimme", 14, Color.WHITE));
        voiceSpinner = new Spinner(this);
        root.addView(voiceSpinner);

        rateLabel = text("", 14, Color.WHITE);
        root.addView(rateLabel);
        rateBar = new SeekBar(this);
        rateBar.setMax(100); // 0,50 … 1,50
        rateBar.setProgress(Math.round(prefs.rate() * 100) - 50);
        root.addView(rateBar);

        pitchLabel = text("", 14, Color.WHITE);
        root.addView(pitchLabel);
        pitchBar = new SeekBar(this);
        pitchBar.setMax(100); // 0,50 … 1,50
        pitchBar.setProgress(Math.round(prefs.pitch() * 100) - 50);
        root.addView(pitchBar);

        pauseLabel = text("", 14, Color.WHITE);
        root.addView(pauseLabel);
        pauseBar = new SeekBar(this);
        pauseBar.setMax(30); // 0,5 … 3,5 Sekunden
        pauseBar.setProgress(Math.round(prefs.pauseMs() / 100f) - 5);
        root.addView(pauseBar);
        updateVoiceLabels();

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { updateVoiceLabels(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { saveVoice(); }
        };
        rateBar.setOnSeekBarChangeListener(l);
        pitchBar.setOnSeekBarChangeListener(l);
        pauseBar.setOnSeekBarChangeListener(l);

        root.addView(space(16));
        root.addView(text("Premium-Stimme (optional, ElevenLabs)", 14, Color.WHITE));
        root.addView(text("Deutlich natürlicher und am nächsten am Film-Jarvis. Schlüssel von elevenlabs.io "
                + "(kostenloser Plan: ca. 10 Minuten Sprache pro Monat). Leer lassen = Handy-Stimme oben. "
                + "Standardstimme: „Daniel“ (britisch, ruhig, präzise) – oder die ID einer selbst entworfenen Stimme eintragen.",
                12, 0xFF90A4AE));
        elevenKeyField = new EditText(this);
        elevenKeyField.setHint("ElevenLabs-Schlüssel (leer = aus)");
        elevenKeyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        elevenKeyField.setText(prefs.elevenKey());
        elevenKeyField.setTextColor(Color.WHITE);
        elevenKeyField.setHintTextColor(0xFF607D8B);
        root.addView(elevenKeyField);
        elevenVoiceField = new EditText(this);
        elevenVoiceField.setHint("Stimmen-ID (leer = Daniel)");
        elevenVoiceField.setSingleLine(true);
        elevenVoiceField.setText(prefs.rawElevenVoice());
        elevenVoiceField.setTextColor(Color.WHITE);
        elevenVoiceField.setHintTextColor(0xFF607D8B);
        root.addView(elevenVoiceField);
        elevenPreview = new ElevenTts(this);

        Button preview = button("Probe hören");
        preview.setOnClickListener(v -> {
            saveVoice();
            save();
            if (!prefs.elevenKey().isEmpty()) {
                setStatus("Premium-Stimme wird geladen …");
                elevenPreview.speak(prefs.elevenKey(), prefs.elevenVoice(),
                        "Guten Abend, Sir. Alle Systeme sind einsatzbereit.", prefs.rate(), new ElevenTts.Listener() {
                            @Override public void onStart() { setStatus("✓ Premium-Stimme aktiv."); }
                            @Override public void onDone() { }
                            @Override public void onError(String message) { setStatus("✗ " + message); }
                        });
                return;
            }
            if (previewTts == null || !previewReady) {
                setStatus("Sprachausgabe lädt noch …");
                return;
            }
            Voices.apply(previewTts, prefs, previewEngine);
            previewTts.speak("Guten Abend, Sir. Alle Systeme sind einsatzbereit.",
                    TextToSpeech.QUEUE_FLUSH, null, "preview");
        });
        root.addView(preview);

        loadPreviewEngine(activeEngine);
    }

    /** Startet die Probe-Sprachausgabe mit der Engine und füllt die Stimmenliste. */
    private void loadPreviewEngine(String engine) {
        if (previewTts != null) previewTts.shutdown();
        previewReady = false;
        previewEngine = engine;
        voiceSpinner.setAdapter(adapter(java.util.Collections.singletonList("Stimmen werden geladen …")));
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = Voices.create(this, engine, status -> {
            if (holder[0] != previewTts) return;
            if (status != TextToSpeech.SUCCESS) {
                setStatus("Diese Sprachausgabe startet nicht. Bitte eine andere Engine wählen.");
                return;
            }
            previewReady = true;
            voices = Voices.list(previewTts);
            Voice chosen = Voices.choose(previewTts, prefs, engine);
            List<String> labels = new ArrayList<>();
            int selected = 0;
            for (int i = 0; i < voices.size(); i++) {
                labels.add(Voices.label(voices.get(i)));
                if (chosen != null && voices.get(i).getName().equals(chosen.getName())) selected = i;
            }
            if (labels.isEmpty()) labels.add("Standardstimme der Engine");
            voiceSpinner.setAdapter(adapter(labels));
            voiceSpinner.setSelection(selected);
            voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                    saveVoice();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
        });
        previewTts = holder[0];
    }

    private ArrayAdapter<String> adapter(List<String> labels) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private void updateVoiceLabels() {
        rateLabel.setText(String.format(java.util.Locale.GERMANY, "Tempo: %.2f", (rateBar.getProgress() + 50) / 100f));
        pitchLabel.setText(String.format(java.util.Locale.GERMANY, "Tonhöhe: %.2f (kleiner = tiefer)", (pitchBar.getProgress() + 50) / 100f));
        pauseLabel.setText(String.format(java.util.Locale.GERMANY,
                "Sprechpause bis zum Absenden: %.1f s (länger = mehr Zeit zum Ausreden)", (pauseBar.getProgress() + 5) / 10f));
    }

    private void saveVoice() {
        prefs.setRate((rateBar.getProgress() + 50) / 100f);
        prefs.setPitch((pitchBar.getProgress() + 50) / 100f);
        prefs.setPauseMs((pauseBar.getProgress() + 5) * 100);
        int pos = voiceSpinner.getSelectedItemPosition();
        if (previewReady && voices != null && pos >= 0 && pos < voices.size()) {
            prefs.setVoiceName(voices.get(pos).getName());
        }
    }

    @Override
    protected void onDestroy() {
        if (previewTts != null) previewTts.shutdown();
        if (elevenPreview != null) elevenPreview.stop();
        super.onDestroy();
    }

    /** Kleine Testanfrage (kostet Bruchteile eines Cents) und zeigt das genaue Ergebnis. */
    private void testKey() {
        save();
        if (prefs.apiKey().isEmpty()) {
            setStatus("Bitte zuerst einen Schlüssel einfügen.");
            return;
        }
        setStatus("Teste Schlüssel …");
        org.json.JSONArray messages = new org.json.JSONArray();
        try {
            messages.put(new org.json.JSONObject().put("role", "user").put("content", "Antworte nur mit: OK"));
        } catch (org.json.JSONException ignored) {
            return;
        }
        ClaudeClient.Callback cb = new ClaudeClient.Callback() {
            @Override
            public void onResult(String text) {
                setStatus("✓ Schlüssel funktioniert (Modell " + prefs.model() + ").");
            }

            @Override
            public void onError(String message) {
                setStatus("✗ " + message);
            }
        };
        if (prefs.isGemini()) new GeminiClient().send(prefs.apiKey(), prefs.model(), "Test.", messages, cb);
        else new ClaudeClient().send(prefs.apiKey(), prefs.model(), "Test.", messages, cb);
    }

    private void save() {
        prefs.setApiKey(keyField.getText().toString());
        if (keyInfo != null) keyInfo.setText(prefs.apiKeyPreview());
        String model = modelField.getText().toString().trim();
        if (!model.isEmpty() && !Prefs.isValidModel(model)) {
            modelField.setText("");
            model = "";
            setStatus("„Modell“ enthielt keinen gültigen Namen und wurde geleert – Jarvis wählt automatisch.");
        }
        prefs.setModel(model);
        prefs.setTftMode(tftSwitch.isChecked());
        if (memoryField != null) prefs.setMemory(memoryField.getText().toString());
        if (elevenKeyField != null) prefs.setElevenKey(elevenKeyField.getText().toString());
        if (elevenVoiceField != null) prefs.setElevenVoice(elevenVoiceField.getText().toString());
        Backup.autoSave(this, prefs);
    }

    private void updateBackupInfo() {
        if (backupInfo == null) return;
        backupInfo.setText(prefs.backupUri().isEmpty()
                ? "Noch keine Sicherung eingerichtet."
                : "✓ Automatische Sicherung aktiv.");
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
        updateBackupInfo();
    }

    /** Zeigt nach einer Wiederherstellung die übernommenen Werte an. */
    private void refreshFromPrefs() {
        memoryField.setText(prefs.memory());
        modelField.setText(prefs.rawModel());
        elevenVoiceField.setText(prefs.rawElevenVoice());
        tftSwitch.setChecked(prefs.tftMode());
        rateBar.setProgress(Math.round(prefs.rate() * 100) - 50);
        pitchBar.setProgress(Math.round(prefs.pitch() * 100) - 50);
        pauseBar.setProgress(Math.round(prefs.pauseMs() / 100f) - 5);
        updateVoiceLabels();
        String active = Voices.resolveEngine(this, prefs);
        loadPreviewEngine(active); // zuerst laden, damit die Auswahl unten nichts zurücksetzt
        for (int i = 0; i < engines.size(); i++) {
            if (engines.get(i).pkg.equals(active)) engineSpinner.setSelection(i);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Jarvis kann sich im Hintergrund Neues gemerkt haben.
        if (memoryField != null) memoryField.setText(prefs.memory());
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
        } else if (requestCode == REQ_BACKUP_CREATE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                useAsBackupTarget(data.getData());
                boolean ok = Backup.write(this, prefs, data.getData());
                setStatus(ok ? "✓ Sicherung eingerichtet. Sie aktualisiert sich ab jetzt automatisch."
                        : "✗ Die Sicherungsdatei ließ sich nicht schreiben.");
            }
        } else if (requestCode == REQ_BACKUP_OPEN) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    int count = Backup.apply(prefs, Backup.read(this, data.getData()));
                    useAsBackupTarget(data.getData()); // künftig in dieselbe Datei sichern
                    refreshFromPrefs();
                    setStatus("✓ Sicherung wiederhergestellt: " + count + " Gedächtniseinträge und deine Einstellungen.");
                } catch (Exception e) {
                    setStatus("✗ Das ist keine gültige Jarvis-Sicherung.");
                }
            }
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
