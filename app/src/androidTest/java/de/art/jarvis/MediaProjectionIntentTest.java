package de.art.jarvis;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.os.Build;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Prüft den echten Freigabe-Intent der Activity ohne Zustimmung, Capture oder API-Aufruf. */
@RunWith(AndroidJUnit4.class)
public class MediaProjectionIntentTest {
    private Instrumentation instrumentation;
    private Activity activity;

    @After public void cleanup() {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
        }
    }

    @Test public void freigabeIntentFragtNurImOpenAiTestDasGanzeDisplayAn() {
        assumeTrue(Build.VERSION.SDK_INT >= 34);
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        activity = instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            try {
                Method factory = MainActivity.class.getDeclaredMethod("screenCaptureIntent");
                factory.setAccessible(true);
                Intent request = (Intent) factory.invoke(activity);
                assertNotNull("Android-Freigabe-Activity ist explizit adressiert", request.getComponent());
                Bundle extras = request.getExtras();
                assertNotNull("Android trägt die Capture-Konfiguration in den Intent ein", extras);
                MediaProjectionConfig config = null;
                // Der Extra-Schlüssel ist keine öffentliche SDK-Konstante. Den tatsächlichen
                // Parcelable-Wert lesen, ohne einen privaten Android-Schlüsselnamen festzuschreiben.
                for (String key : extras.keySet()) {
                    Object value = extras.get(key);
                    if (value instanceof MediaProjectionConfig) {
                        assertNull("Genau eine Capture-Konfiguration", config);
                        config = (MediaProjectionConfig) value;
                    }
                }
                assertNotNull("Der echte Intent enthält eine MediaProjectionConfig", config);
                if (BuildConfig.OPENAI_TEST) {
                    assertEquals(MediaProjectionConfig.createConfigForDefaultDisplay(), config);
                    assertNotEquals(MediaProjectionConfig.createConfigForUserChoice(), config);
                } else {
                    assertEquals("Normale Varianten behalten die bisherige Auswahl",
                            MediaProjectionConfig.createConfigForUserChoice(), config);
                }
                assertFalse("Intent-Erzeugung startet weder Dienst noch Mikrofon", JarvisService.isRunning());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Freigabe-Intent der Activity konnte nicht geprüft werden", e);
            }
        });
    }
}
