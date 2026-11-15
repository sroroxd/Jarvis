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
        for (String name : new String[]{"apiKey", "elevenKey", "githubToken", "agentToken"}) assertFalse(exported.has(name));
        assertFalse(output.contains("test-claude"));
        assertFalse(output.contains("test-eleven"));
        assertFalse(output.contains("test-github"));
        assertFalse(output.contains("test-agent"));
        assertFalse(output.contains("test-openai"));
        assertTrue(exported.getString("memory").contains("Testeintrag"));
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
}
