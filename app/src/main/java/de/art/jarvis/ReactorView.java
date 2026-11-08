package de.art.jarvis;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Der große Sprechen-Knopf im Stil eines Arc-Reaktors: leuchtender Kern, drehende Ringe.
 * Jeder Zustand hat eigene Farbe und Bewegung – beim Zuhören pulsiert er mit der Stimme.
 */
public class ReactorView extends View {
    public enum Mode { OFF, IDLE, LISTENING, THINKING, SPEAKING }

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final long start = SystemClock.uptimeMillis();

    private Mode mode = Mode.OFF;
    private int color;
    private ValueAnimator colorAnimator;
    private float level;
    private float targetLevel;
    private String label = "STARTEN";

    private RadialGradient coreShader;
    private RadialGradient glowShader;
    private int shaderColor;
    private float shaderRadius;

    public ReactorView(Context context) {
        this(context, null);
    }

    public ReactorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        color = Palette.off(context);
        ringPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        pulsePaint.setStyle(Paint.Style.STROKE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textPaint.setLetterSpacing(0.18f);
        setClickable(true);
        setFocusable(true);
        setContentDescription("Jarvis starten");
    }

    /** Zustand setzen; die Farbe geht weich über. */
    public void setMode(Mode m, String newLabel) {
        label = newLabel;
        setContentDescription(newLabel);
        if (m == mode) {
            invalidate();
            return;
        }
        mode = m;
        if (m != Mode.LISTENING) targetLevel = 0f;
        int to = colorFor(m);
        if (colorAnimator != null) colorAnimator.cancel();
        colorAnimator = ValueAnimator.ofArgb(color, to);
        colorAnimator.setDuration(380);
        colorAnimator.setInterpolator(new DecelerateInterpolator());
        colorAnimator.addUpdateListener(a -> {
            color = (int) a.getAnimatedValue();
            invalidate();
        });
        colorAnimator.start();
    }

    /** Lautstärke der Stimme beim Zuhören (0 … 1). */
    public void setLevel(float l) {
        targetLevel = Math.max(0f, Math.min(1f, l));
    }

    private int colorFor(Mode m) {
        Context c = getContext();
        switch (m) {
            case IDLE: return c.getColor(R.color.state_idle);
            case LISTENING: return c.getColor(R.color.state_listening);
            case THINKING: return c.getColor(R.color.state_thinking);
            case SPEAKING: return c.getColor(R.color.state_speaking);
            default: return c.getColor(R.color.state_off);
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        boolean was = isPressed();
        super.setPressed(pressed);
        if (was != pressed) {
            animate().scaleX(pressed ? 0.95f : 1f).scaleY(pressed ? 0.95f : 1f)
                    .setDuration(pressed ? 90 : 160).start();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) size = h;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) size = w;
        int max = Math.round(320 * getResources().getDisplayMetrics().density);
        size = Math.min(size, max);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) * 0.94f;
        if (r <= 0) return;

        // Stimme glätten, damit der Kern nicht zappelt.
        level += (targetLevel - level) * 0.25f;
        boolean off = mode == Mode.OFF;
        float breathe = off ? 0f : (float) (0.5 + 0.5 * Math.sin(t * (mode == Mode.SPEAKING ? 5.2 : 1.6)));
        float energy;
        switch (mode) {
            case LISTENING: energy = 0.55f + 0.45f * level; break;
            case SPEAKING: energy = 0.45f + 0.35f * breathe; break;
            case THINKING: energy = 0.5f + 0.2f * breathe; break;
            case IDLE: energy = 0.32f + 0.12f * breathe; break;
            default: energy = 0.12f;
        }
        ensureShaders(r);

        // 1. Weicher Schein hinter allem
        glowPaint.setShader(glowShader);
        glowPaint.setAlpha((int) (255 * Math.min(1f, 0.35f + energy * 0.65f)));
        canvas.drawCircle(cx, cy, r, glowPaint);

        // 2. Äußerer Skalenring mit Strichen, dreht sich langsam
        float tickSpin = t * (mode == Mode.THINKING ? 40f : 6f);
        tickPaint.setColor(withAlpha(color, off ? 70 : 150));
        tickPaint.setStrokeWidth(r * 0.012f);
        canvas.save();
        canvas.rotate(tickSpin, cx, cy);
        for (int i = 0; i < 72; i++) {
            float len = (i % 6 == 0) ? r * 0.06f : r * 0.03f;
            canvas.drawLine(cx, cy - r * 0.97f, cx, cy - r * 0.97f + len, tickPaint);
            canvas.rotate(5f, cx, cy);
        }
        canvas.restore();

        // 3. Segmentierter Ring (drei Bögen), dreht gegenläufig – beim Nachdenken schnell
        float arcSpin = -t * (mode == Mode.THINKING ? 220f : mode == Mode.LISTENING ? 60f : 18f);
        float arcR = r * 0.80f;
        arcRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
        arcPaint.setStrokeWidth(r * 0.045f);
        arcPaint.setColor(withAlpha(color, off ? 90 : 220));
        for (int i = 0; i < 3; i++) {
            canvas.drawArc(arcRect, arcSpin + i * 120f, 78f, false, arcPaint);
        }

        // 4. Feiner Innenring
        ringPaint.setStrokeWidth(r * 0.012f);
        ringPaint.setColor(withAlpha(color, off ? 60 : 140));
        canvas.drawCircle(cx, cy, r * 0.66f, ringPaint);

        // 5. Pulswellen beim Zuhören und Sprechen
        if (mode == Mode.LISTENING || mode == Mode.SPEAKING) {
            for (int i = 0; i < 2; i++) {
                float phase = ((t * (mode == Mode.LISTENING ? 0.9f : 1.3f)) + i * 0.5f) % 1f;
                float pr = r * (0.5f + 0.45f * phase);
                pulsePaint.setStrokeWidth(r * (0.02f + 0.03f * level));
                pulsePaint.setColor(withAlpha(color, (int) (170 * (1f - phase))));
                canvas.drawCircle(cx, cy, pr, pulsePaint);
            }
        }

        // 6. Leuchtender Kern
        float coreR = r * (0.46f + 0.06f * (mode == Mode.LISTENING ? level : breathe * 0.5f));
        corePaint.setShader(coreShader);
        corePaint.setAlpha(off ? 150 : 255);
        canvas.save();
        float scale = coreR / (r * 0.46f);
        canvas.scale(scale, scale, cx, cy);
        canvas.drawCircle(cx, cy, r * 0.46f, corePaint);
        canvas.restore();

        // 7. Beschriftung
        textPaint.setTextSize(r * 0.095f);
        textPaint.setAlpha(off ? 200 : 240);
        float ty = cy - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(label, cx, ty, textPaint);

        if (getWindowVisibility() == VISIBLE && isAttachedToWindow()) {
            long gap = frameGapMs(mode);
            if (gap <= 0) postInvalidateOnAnimation();
            else postInvalidateDelayed(gap);
        }
    }

    /**
     * Pause bis zum nächsten Bild. Gemessen auf Arts S24 Ultra (Build 107): Der Knopf zeichnete im Ruhezustand
     * durchgehend 61 Bilder pro Sekunde, die App lag dabei bei 27 % CPU, und das Display kam nicht in seine
     * sparsame niedrige Bildrate. Langsame Drehungen (6–60 °/s) und das Atmen brauchen das nicht: aus 12, bereit
     * und zuhören 30 Bilder pro Sekunde. Nur Nachdenken (220 °/s) und Sprechen bleiben flüssig.
     */
    static long frameGapMs(Mode m) {
        switch (m) {
            case THINKING:
            case SPEAKING:
                return 0;
            case OFF:
                return 83;
            default:
                return 33;
        }
    }

    /** Farbverläufe nur neu bauen, wenn sich Farbe oder Größe ändert. */
    private void ensureShaders(float r) {
        if (coreShader != null && shaderColor == color && shaderRadius == r) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        int light = blend(color, Color.WHITE, 0.75f);
        int dark = blend(color, Color.BLACK, 0.55f);
        coreShader = new RadialGradient(cx, cy, r * 0.46f,
                new int[]{light, blend(color, Color.WHITE, 0.25f), color, dark},
                new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP);
        glowShader = new RadialGradient(cx, cy, r,
                new int[]{withAlpha(color, 150), withAlpha(color, 60), withAlpha(color, 0)},
                new float[]{0.3f, 0.65f, 1f}, Shader.TileMode.CLAMP);
        shaderColor = color;
        shaderRadius = r;
    }

    private static int withAlpha(int c, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(c), Color.green(c), Color.blue(c));
    }

    private static int blend(int a, int b, float f) {
        float g = 1f - f;
        return Color.rgb(
                (int) (Color.red(a) * g + Color.red(b) * f),
                (int) (Color.green(a) * g + Color.green(b) * f),
                (int) (Color.blue(a) * g + Color.blue(b) * f));
    }
}
