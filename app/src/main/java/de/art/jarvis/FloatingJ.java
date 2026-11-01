package de.art.jarvis;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Das optionale schwebende „J“ (Einstellungen → Anzeige). Antippen = sprechen,
 * lange drücken = neues Gespräch, ziehen = verschieben (Position wird gespeichert).
 * Zeigt keinen Text mehr – dafür gibt es die nicht anklickbaren Untertitel.
 */
final class FloatingJ {
    interface Callbacks {
        void onTap();

        void onLongPress();
    }

    private final Context context;
    private final Prefs prefs;
    private final Callbacks callbacks;
    private final WindowManager wm;
    private TextView view;
    private WindowManager.LayoutParams lp;

    FloatingJ(Context context, Prefs prefs, Callbacks callbacks) {
        this.context = context;
        this.prefs = prefs;
        this.callbacks = callbacks;
        this.wm = context.getSystemService(WindowManager.class);
    }

    void show(JarvisService.State state) {
        if (view != null) return;
        int size = dp(48);
        TextView t = new TextView(context);
        t.setText("J");
        t.setTextColor(Color.WHITE);
        t.setTextSize(19);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setElevation(dp(8));
        t.setContentDescription("Jarvis");
        lp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs.bubbleX(dp(8));
        lp.y = prefs.bubbleY(dp(220));
        clamp();
        t.setOnTouchListener(new DragOrTap());
        try {
            wm.addView(t, lp);
            view = t;
            setState(state);
        } catch (Exception e) {
            view = null;
        }
    }

    void setState(JarvisService.State state) {
        if (view == null) return;
        int color = Palette.color(context, state);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColors(new int[]{lighten(color), color});
        d.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        d.setGradientRadius(dp(30));
        d.setStroke(dp(2), state == JarvisService.State.LISTENING ? 0xCCFFFFFF : 0x55FFFFFF);
        view.setBackground(d);
    }

    void setCaptureHidden(boolean hidden) {
        if (view != null) view.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
    }

    void remove() {
        if (view != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
                // bereits entfernt
            }
            view = null;
        }
    }

    private void clamp() {
        Rect b = wm.getCurrentWindowMetrics().getBounds();
        lp.x = Math.max(0, Math.min(lp.x, b.width() - dp(48)));
        lp.y = Math.max(0, Math.min(lp.y, b.height() - dp(48)));
    }

    private final class DragOrTap implements View.OnTouchListener {
        private float downX, downY;
        private int startX, startY;
        private long downTime;
        private boolean dragging;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    downTime = System.currentTimeMillis();
                    dragging = false;
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > dp(10)) dragging = true;
                    if (dragging) {
                        lp.x = startX + (int) dx;
                        lp.y = startY + (int) dy;
                        clamp();
                        wm.updateViewLayout(view, lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    if (dragging) {
                        prefs.setBubblePosition(lp.x, lp.y);
                    } else if (System.currentTimeMillis() - downTime > 600) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        callbacks.onLongPress();
                    } else {
                        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        callbacks.onTap();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    return true;
                default:
                    return false;
            }
        }
    }

    private static int lighten(int c) {
        int r = Math.min(255, Color.red(c) + 70);
        int g = Math.min(255, Color.green(c) + 70);
        int b = Math.min(255, Color.blue(c) + 70);
        return Color.rgb(r, g, b);
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
