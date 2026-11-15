package de.art.jarvis;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Echte Activity-Views unter kleinen Höhen; keine Verbindung, Mikrofon- oder Bildschirmfreigabe. */
@RunWith(AndroidJUnit4.class)
public class MainActivityWindowTest {
    private Instrumentation instrumentation;
    private Context context;
    private Activity activity;
    private Prefs prefs;

    @Before public void prepare() {
        assumeTrue(BuildConfig.OPENAI_TEST && BuildConfig.DEBUG);
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        clearPreferences();
        prefs = new Prefs(context);
        prefs.setOpenAiSelected(true);
        prefs.setOpenAiKey("local-window-test-only");
    }

    @After public void cleanup() {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
        }
        if (context != null) clearPreferences();
    }

    private void clearPreferences() {
        for (String name : new String[]{"jarvis", "jarvis_secrets"}) {
            assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit());
        }
    }

    @Test public void bereitschaftFolgtAnbieterUndSchluesselOhneSitzungsstart() {
        launch(false);
        instrumentation.runOnMainSync(() -> {
            layout(root(), 720);
            String detail = detail();
            assertTrue("Ausgewähltes OpenAI mit Testschlüssel ist startbereit", detail.contains("OpenAI Realtime bereit"));
            assertFalse(detail.contains("OpenAI auswählen"));
            assertFalse(detail.contains("local-window-test-only"));
            assertFalse(JarvisService.isRunning());
            prefs.setOpenAiKey("");
            ((MainActivity) activity).onStatusChanged();
            assertTrue(detail().contains("OpenAI-Schlüssel fehlt"));
            assertTrue("Anzeige wechselt keinen Anbieter", prefs.openAiSelected());
            prefs.setOpenAiSelected(false);
            ((MainActivity) activity).onStatusChanged();
            assertTrue(detail().contains("OpenAI Realtime auswählen"));
            assertFalse(prefs.openAiSelected());
        });
    }

    @Test public void kleineFensterBehaltenKernStatusStopUndErreichbareZusatzbedienung() {
        launch(true); // Bestehender Debug-Vorschaumodus zeigt einen aktiven Zustand ohne Dienststart.
        instrumentation.runOnMainSync(() -> {
            ViewGroup root = root();
            for (int height : new int[]{400, 320, 720, 400, 320, 720}) {
                layout(root, height);
                if (height < 600) {
                    View reactor = activity.findViewById(R.id.reactor);
                    assertTrue("Kern bleibt deutlich bedienbar bei " + height + "dp", reactor.getHeight() >= dp(96));
                    assertTrue(reactor.isClickable());
                    fullyVisible(root, reactor);
                    fullyVisible(root, activity.findViewById(R.id.mainStatusCard));
                    View stop = activity.findViewById(R.id.stopButton);
                    assertEquals(View.VISIBLE, stop.getVisibility());
                    assertTrue(stop.isClickable());
                    fullyVisible(root, stop);
                    View scroll = activity.findViewById(R.id.logScroll);
                    assertTrue("Scrollbereich zeigt ein ganzes Bedienelement", scroll.getHeight() - scroll.getPaddingTop()
                            - scroll.getPaddingBottom() >= dp(48));
                    for (int id : new int[]{R.id.settingsButton, R.id.chipListen, R.id.chipGame,
                            R.id.chipScreen, R.id.mainOpenAiImage}) {
                        View control = activity.findViewById(id);
                        Rect rect = new Rect();
                        control.getDrawingRect(rect);
                        control.requestRectangleOnScreen(rect, true);
                        fullyVisible(root, control);
                    }
                    assertTrue(activity.findViewById(R.id.statusDetail).isClickable());
                    assertTrue(((TextView) activity.findViewById(R.id.statusDetail)).getText().toString().contains("Kostenpflichtig"));
                    assertTrue(activity.findViewById(R.id.statusDetail).getContentDescription().toString().contains("Antippen"));
                    saveWindow(root, "openai-fenster-" + height + ".png");
                } else {
                    assertSame(root, activity.findViewById(R.id.mainHeader).getParent());
                    assertSame(root, activity.findViewById(R.id.mainOpenAiImage).getParent());
                    assertSame(root, activity.findViewById(R.id.hint).getParent());
                    assertNotSame(activity.findViewById(R.id.mainTranscriptContent),
                            activity.findViewById(R.id.mainStatusChips).getParent());
                    assertTrue(root.indexOfChild(activity.findViewById(R.id.mainOpenAiImage))
                            < root.indexOfChild(activity.findViewById(R.id.mainReactorContainer)));
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.subtitle).getVisibility());
                }
            }
            assertFalse("Vorschaumodus stellt keine API-Verbindung her", JarvisService.isRunning());
        });
    }

    @Test public void kompakterStatusOeffnetVollstaendigenBericht() {
        launch(false);
        instrumentation.runOnMainSync(() -> {
            layout(root(), 400);
            assertTrue(activity.findViewById(R.id.statusDetail).performClick());
        });
        instrumentation.waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo window = instrumentation.getUiAutomation().getRootInActiveWindow();
        assertNotNull("Statusdialog im Vordergrund", window);
        assertFalse("Vollständige Bereitschaftsmeldung im Dialog lesbar",
                window.findAccessibilityNodeInfosByText("OpenAI Realtime bereit").isEmpty());
    }

    private void launch(boolean activePreview) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (activePreview) intent.putExtra("demo", "LISTENING");
        activity = instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
    }

    private String detail() {
        return ((TextView) activity.findViewById(R.id.statusDetail)).getText().toString();
    }

    private ViewGroup root() {
        return (ViewGroup) ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
    }

    private void layout(ViewGroup root, int heightDp) {
        // Mehrere Durchläufe wie bei einer Fenstergrößenänderung: Der erste ordnet um, danach
        // misst Android die neue Hierarchie. Kein direkter Aufruf privater Layout-Implementierung.
        for (int i = 0; i < 3; i++) {
            root.forceLayout();
            root.measure(View.MeasureSpec.makeMeasureSpec(dp(360), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY));
            root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        }
    }

    private void fullyVisible(ViewGroup root, View view) {
        Rect bounds = new Rect();
        view.getDrawingRect(bounds);
        root.offsetDescendantRectToMyCoords(view, bounds);
        assertTrue("Oberkante im Fenster: " + view.getId(), bounds.top >= root.getPaddingTop());
        assertTrue("Unterkante im Fenster: " + view.getId(), bounds.bottom <= root.getHeight() - root.getPaddingBottom());
        Rect visible = new Rect();
        assertTrue("Bedienelement sichtbar: " + view.getId(), view.getLocalVisibleRect(visible));
        assertEquals("Keine vertikale Abschneidung: " + view.getId(), view.getHeight(), visible.height());
        assertEquals("Keine horizontale Abschneidung: " + view.getId(), view.getWidth(), visible.width());
    }

    private void saveWindow(View root, String name) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(root.getWidth(), root.getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888);
        try (java.io.FileOutputStream out = context.openFileOutput(name, Context.MODE_PRIVATE)) {
            root.draw(new android.graphics.Canvas(bitmap));
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        } catch (java.io.IOException e) {
            throw new AssertionError("Fenster-Vorschaubild konnte nicht gespeichert werden", e);
        } finally {
            bitmap.recycle();
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
