package de.art.jarvis;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Untertitel-Leiste am Bildschirmrand: halbtransparent, nicht anklickbar, verschwindet von selbst.
 *
 * <p>Das Fenster ist {@code FLAG_NOT_TOUCHABLE} und höchstens zu 75 % deckend – so leitet Android jede
 * Berührung an die App darunter weiter (ab Android 12 werden Berührungen durch stärker deckende
 * Überlagerungen blockiert). Im Spielmodus geht also jeder Fingertipp ungehindert ins Spiel.
 */
final class Subtitles {
    /** Fensterdeckkraft – muss ≤ 0,8 bleiben, sonst blockiert Android Berührungen darunter. */
    private static final float WINDOW_ALPHA = 0.75f;

    private final Context context;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;
    private TextView view;
    private WindowManager.LayoutParams lp;
    private boolean captureHidden;

    Subtitles(Context context) {
        this.context = context;
        this.wm = context.getSystemService(WindowManager.class);
    }

    /**
     * Zeigt einen Text. {@code compact} = Spielmodus (kleiner, schmaler, zwei Zeilen).
     * {@code hideAfterMs} &gt; 0 blendet ihn danach aus, sonst bleibt er bis zum nächsten Aufruf.
     */
    void show(String text, boolean compact, boolean top, long hideAfterMs) {
        if (text == null || text.trim().isEmpty() || !Settings.canDrawOverlays(context)) return;
        main.removeCallbacks(hide);
        if (view == null) create();
        if (view == null) return;
        int screenWidth = wm.getCurrentWindowMetrics().getBounds().width();
        view.setMaxWidth((int) (screenWidth * (compact ? 0.46f : 0.86f)));
        view.setMaxLines(compact ? 2 : 3);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 12 : 14);
        view.setText(text.trim());
        lp.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        lp.y = top ? (compact ? dp(4) : statusBarHeight() + dp(8)) : dp(compact ? 6 : 28);
        try {
            wm.updateViewLayout(view, lp);
        } catch (Exception ignored) {
            return;
        }
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() < 1f) {
            view.animate().cancel();
            view.setAlpha(0f);
            view.setVisibility(captureHidden ? View.INVISIBLE : View.VISIBLE);
            view.animate().alpha(1f).setDuration(160).start();
        }
        if (hideAfterMs > 0) main.postDelayed(hide, hideAfterMs);
    }

    void hideAfter(long ms) {
        main.removeCallbacks(hide);
        if (view != null && view.getVisibility() == View.VISIBLE) main.postDelayed(hide, ms);
    }

    void hide() {
        main.removeCallbacks(hide);
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        view.animate().cancel();
        view.animate().alpha(0f).setDuration(260).withEndAction(() -> {
            if (view != null) view.setVisibility(View.GONE);
        }).start();
    }

    boolean isShowing() {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    /** Kurz ausblenden, damit die Leiste nicht auf dem Bildschirmfoto für Jarvis landet. */
    void setCaptureHidden(boolean hidden) {
        captureHidden = hidden;
        if (view == null) return;
        if (hidden && view.getVisibility() == View.VISIBLE) view.setVisibility(View.INVISIBLE);
        else if (!hidden && view.getVisibility() == View.INVISIBLE) view.setVisibility(View.VISIBLE);
    }

    void destroy() {
        main.removeCallbacks(hide);
        if (view != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
                // bereits entfernt
            }
            view = null;
        }
    }

    private void create() {
        TextView t = new TextView(context);
        t.setTextColor(0xFFE8F4FF);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLineSpacing(0, 1.1f);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD9060C16);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x664FB6FF);
        t.setBackground(bg);
        t.setVisibility(View.GONE);
        // Rein informativ – keine Berührung, kein Fokus.
        t.setClickable(false);
        t.setFocusable(false);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.alpha = WINDOW_ALPHA;
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try {
            wm.addView(t, p);
            view = t;
            lp = p;
        } catch (Exception e) {
            view = null;
        }
    }

    private int statusBarHeight() {
        Resources r = context.getResources();
        int id = r.getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? r.getDimensionPixelSize(id) : dp(24);
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
