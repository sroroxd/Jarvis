package de.art.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Läuft auf dem Android-Emulator mit echtem AndroidKeyStore, niemals mit echten API-Schlüsseln. */
@RunWith(AndroidJUnit4.class)
public class CredentialsTest {
    private Context context;
    private SharedPreferences legacy;
    private SharedPreferences encrypted;

    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        legacy = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE);
        encrypted = context.getSharedPreferences("jarvis_secrets", Context.MODE_PRIVATE);
        clear();
    }

    @After public void clear() {
        if (legacy != null) assertTrue(legacy.edit().clear().commit());
        if (encrypted != null) assertTrue(encrypted.edit().clear().commit());
    }

    @Test public void bestehendeSchluesselWerdenOhneVerlustMigriert() {
        String[] names = {"api_key", "eleven_key", "github_token", "agent_token", "openai_key"};
        SharedPreferences.Editor seed = legacy.edit().putBoolean("backup_keys", true);
        for (String name : names) seed.putString(name, "test-geheim-" + name);
        assertTrue(seed.commit());
        Prefs p = new Prefs(context);
        assertEquals("test-geheim-api_key", p.apiKey());
        assertEquals("test-geheim-eleven_key", p.elevenKey());
        assertEquals("test-geheim-github_token", p.githubToken());
        assertEquals("test-geheim-agent_token", p.agentToken());
        assertEquals("test-geheim-openai_key", p.openAiKey());
        for (String name : names) {
            assertFalse(legacy.contains(name));
            assertTrue(encrypted.contains(name));
            assertFalse(encrypted.getString(name, "").contains("test-geheim"));
        }
        assertEquals("", p.credentialsError());
        assertEquals(p.apiKey(), new Prefs(context).apiKey());
        p.setApiKey("");
        assertEquals("", new Prefs(context).apiKey());
    }

    @Test public void alterBackupImportExportiertNiemalsErneutSchluessel() throws Exception {
        Prefs p = new Prefs(context);
        if (BuildConfig.OPENAI_TEST) p.setOpenAiSelected(true);
        Backup.apply(p, new JSONObject().put("apiKey", "test-claude").put("elevenKey", "test-eleven")
                .put("githubToken", "test-github").put("agentToken", "test-agent")
                .put("memory", "- Ein Testeintrag").toString());
        assertEquals("test-claude", p.apiKey());
        assertEquals("test-eleven", p.elevenKey());
        assertEquals("test-github", p.githubToken());
        assertEquals("test-agent", p.agentToken());
        String output = Backup.toJson(p);
        p.setOpenAiKey("test-openai");
        output = Backup.toJson(p);
        JSONObject exported = new JSONObject(output);
        assertEquals(p.openAiSelected(), exported.getBoolean("openaiSelected"));
        for (String name : new String[]{"apiKey", "elevenKey", "githubToken", "agentToken"}) assertFalse(exported.has(name));
        assertFalse(output.contains("test-claude"));
        assertFalse(output.contains("test-eleven"));
        assertFalse(output.contains("test-github"));
        assertFalse(output.contains("test-agent"));
        assertFalse(output.contains("test-openai"));
        assertTrue(exported.getString("memory").contains("Testeintrag"));
        if (BuildConfig.OPENAI_TEST) {
            p.setOpenAiSelected(false);
            Backup.apply(p, output);
            assertTrue("Sicherung stellt den Anbieter wieder her", p.openAiSelected());
        }
    }

    @Test public void beschaedigteSchluesselWerdenNichtAlsKlartextGelesenOderLeerUeberschrieben() {
        assertTrue(encrypted.edit().putString("api_key", "beschaedigt").commit());
        Prefs p = new Prefs(context);
        assertEquals("", p.apiKey());
        assertFalse(p.credentialsError().isEmpty());
        p.setApiKey("");
        assertEquals("beschaedigt", encrypted.getString("api_key", ""));
        p.setApiKey("test-neuer-schluessel");
        assertEquals("test-neuer-schluessel", new Prefs(context).apiKey());
    }

    @Test public void openAiDienstBleibtOhneActivityImVordergrundOhneSchluesselLokal() throws Exception {
        if (!BuildConfig.OPENAI_TEST) return;
        Prefs p = new Prefs(context);
        p.setOpenAiSelected(true);
        p.setOpenAiKey(""); // Kein API-Aufruf und keine echten Zugangsdaten in diesem Test.
        android.content.Intent activity = new android.content.Intent(context, MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activity);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.content.Intent service = new android.content.Intent(context, JarvisService.class)
                .setAction(JarvisService.ACTION_START);
        try {
            context.startForegroundService(service);
            long end = android.os.SystemClock.elapsedRealtime() + 10000;
            while (!JarvisService.isRunning() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(50);
            assertTrue("Foreground-Service gestartet", JarvisService.isRunning());
            try (android.os.ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME")) {
                try (java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                    byte[] b = new byte[512]; while (in.read(b) != -1) { /* Befehl abwarten */ }
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue("Activity im Hintergrund beendet den Dienst nicht", JarvisService.isRunning());
            assertFalse("Ohne MediaProjection-Freigabe kein Bildschirm", JarvisService.isScreenShared());
        } finally {
            context.stopService(new android.content.Intent(context, JarvisService.class));
            long end = android.os.SystemClock.elapsedRealtime() + 5000;
            while (JarvisService.isRunning() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(50);
        }
    }

    @Test public void providerwechselBlockiertAuchBereitsEingereihteClaudeAnfragen() throws Exception {
        ClaudeClient client = new ClaudeClient();
        client.allowRequests(() -> false);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> result = new java.util.concurrent.atomic.AtomicReference<>("");
        client.send("local-test-only", Prefs.DEFAULT_MODEL, "Test", new org.json.JSONArray(), new ClaudeClient.Callback() {
            @Override public void onResult(String text) { result.set("Unerwartete Antwort"); done.countDown(); }
            @Override public void onError(String message) { result.set(message); done.countDown(); }
        });
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue("Abbruch vor jedem Netzwerkzugriff", result.get().contains("Providerwechsel"));
    }

    @Test public void verspaeteterStartCallbackWartetBisDieActivityWiederSichtbarIst() throws Exception {
        if (!BuildConfig.OPENAI_TEST) return;
        Prefs p = new Prefs(context);
        p.setOpenAiSelected(true);
        p.setOpenAiKey(""); // Ausschließlich lokaler Diensttest, niemals ein API-Aufruf.
        context.stopService(new android.content.Intent(context, JarvisService.class));
        long stoppedAt = android.os.SystemClock.elapsedRealtime() + 5000;
        while (JarvisService.isRunning() && android.os.SystemClock.elapsedRealtime() < stoppedAt) Thread.sleep(50);
        assertFalse(JarvisService.isRunning());
        // Der vorige Diensttest hat HOME gedrückt. Ein Context-Start aus diesem Hintergrund
        // ist keine verlässliche Geräte-Navigation (singleTask/BAL). Über Androids Shell
        // wirklich in die App wechseln; für den ersten Schritt einen frischen Task anlegen.
        String initialLaunch = navigateToMain(true);
        java.util.concurrent.atomic.AtomicReference<android.app.Activity> resumedActivity = new java.util.concurrent.atomic.AtomicReference<>();
        long visibleAt = android.os.SystemClock.elapsedRealtime() + 10000;
        while (resumedActivity.get() == null && android.os.SystemClock.elapsedRealtime() < visibleAt) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (android.app.Activity candidate : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                    if (candidate instanceof MainActivity) resumedActivity.set(candidate);
                }
            });
            if (resumedActivity.get() == null) Thread.sleep(25);
        }
        android.app.Activity activity = resumedActivity.get();
        assertNotNull("MainActivity tatsächlich RESUMED: " + initialLaunch, activity);
        java.util.concurrent.CountDownLatch paused = new java.util.concurrent.CountDownLatch(1);
        androidx.test.runner.lifecycle.ActivityLifecycleCallback lifecycle = (candidate, stage) -> {
            if (candidate == activity && stage == androidx.test.runner.lifecycle.Stage.PAUSED) paused.countDown();
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(lifecycle));
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                JarvisService.reportStartFailure(ForegroundStart.attempt(true, () -> {
                    throw new SecurityException("Interne Android-Details");
                }));
                assertEquals("Start nicht möglich", ((android.widget.TextView) activity.findViewById(R.id.statusText)).getText().toString());
                String message = ((android.widget.TextView) activity.findViewById(R.id.statusDetail)).getText().toString();
                assertTrue("Startfehler bleibt auch im OpenAI-Modus sichtbar", message.contains("Mikrofon-Dienststart"));
                assertFalse(message.contains("Interne Android-Details"));
            });
            try (android.os.ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME");
                 java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                byte[] buffer = new byte[512]; while (in.read(buffer) != -1) { /* HOME vollständig abwarten. */ }
            }
            assertTrue("Activity wurde tatsächlich pausiert", paused.await(5, java.util.concurrent.TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            java.lang.reflect.Field resumed = MainActivity.class.getDeclaredField("resumed");
            resumed.setAccessible(true);
            java.lang.reflect.Field pending = MainActivity.class.getDeclaredField("pendingStart");
            pending.setAccessible(true);
            java.lang.reflect.Method start = MainActivity.class.getDeclaredMethod("startJarvis", int.class, android.content.Intent.class);
            start.setAccessible(true);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try {
                    assertFalse("Activity nach HOME pausiert", resumed.getBoolean(activity));
                    start.invoke(activity, android.app.Activity.RESULT_CANCELED, null);
                    assertNotNull("Verspätetes Ergebnis nur vormerken", pending.get(activity));
                    assertFalse("Kein Mikrofon-FGS-Start im Hintergrund", JarvisService.isRunning());
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            // Hier denselben Task behalten: Nur die echte Rückkehr darf pendingStart verbrauchen.
            String returnLaunch = navigateToMain(false);
            long end = android.os.SystemClock.elapsedRealtime() + 10000;
            while (!JarvisService.isRunning() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(50);
            assertTrue("Start erst nach sichtbarer Rückkehr: " + returnLaunch, JarvisService.isRunning());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try {
                    assertTrue(resumed.getBoolean(activity));
                    assertNull("Startauftrag genau einmal verbraucht", pending.get(activity));
                } catch (IllegalAccessException e) { throw new AssertionError(e); }
            });
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycle));
            context.stopService(new android.content.Intent(context, JarvisService.class));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            long end = android.os.SystemClock.elapsedRealtime() + 5000;
            while (JarvisService.isRunning() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(50);
        }
    }

    private String navigateToMain(boolean freshTask) throws java.io.IOException {
        String command = "am start -W " + (freshTask ? "-f 0x10008000 " : "")
                + "-n " + context.getPackageName() + "/" + MainActivity.class.getName();
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (android.os.ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
             java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return output.toString(java.nio.charset.StandardCharsets.UTF_8.name());
    }

    @Test public void openAiEinstellungenVerbergenNichtAngebundeneFunktionenUndBehaltenSchluessel() throws Exception {
        if (!BuildConfig.OPENAI_TEST) return;
        Prefs p = new Prefs(context);
        p.setOpenAiSelected(true);
        p.setOpenAiKey("local-ui-test-key");
        android.app.Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                new android.content.Intent(context, SettingsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (int id : new int[]{R.id.keyLayout, R.id.modelLayout, R.id.testKey, R.id.speechControlCard,
                        R.id.voiceCard, R.id.wishesCard, R.id.agentCard, R.id.memoryCard, R.id.metaHint,
                        R.id.metaInfo, R.id.metaUpdate, R.id.switchProactive, R.id.switchGameThink}) {
                    assertEquals("Claude-Funktion im OpenAI-Modus verborgen: " + id,
                            android.view.View.GONE, activity.findViewById(id).getVisibility());
                }
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.openAiKeyInput).getVisibility());
                android.widget.EditText key = activity.findViewById(R.id.openAiKeyInput);
                assertTrue("API-Schlüssel standardmäßig maskiert", key.getTransformationMethod()
                        instanceof android.text.method.PasswordTransformationMethod);
                assertFalse(key.getTransformationMethod().getTransformation(key.getText(), key).toString().contains("local-ui-test-key"));
                String help = ((android.widget.TextView) activity.findViewById(R.id.helpText)).getText().toString();
                assertTrue(help.contains("OpenAI-Modus"));
                assertTrue(help.contains("Keine feste Antwortzahl"));
                assertTrue(help.contains("zehn Minuten"));
                assertFalse(help.contains("sechs Antworten"));
                com.google.android.material.materialswitch.MaterialSwitch provider = activity.findViewById(R.id.openAiProvider);
                provider.setChecked(false);
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.keyLayout).getVisibility());
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.wishesCard).getVisibility());
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.metaUpdate).getVisibility());
                assertEquals("local-ui-test-key", new Prefs(context).openAiKey());
                provider.setChecked(true);
                assertEquals(android.view.View.GONE, activity.findViewById(R.id.wishesCard).getVisibility());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            settingsScreenshot("openai-einstellungen.png");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    ((android.widget.ScrollView) activity.findViewById(R.id.settingsScroll)).fullScroll(android.view.View.FOCUS_DOWN));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            settingsScreenshot("openai-hilfe.png");
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    private void settingsScreenshot(String name) throws Exception {
        android.graphics.Bitmap shot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Einstellungsoberfläche sichtbar", shot);
        try (java.io.FileOutputStream out = context.openFileOutput(name, Context.MODE_PRIVATE)) {
            assertTrue(shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        } finally { shot.recycle(); }
    }
}
