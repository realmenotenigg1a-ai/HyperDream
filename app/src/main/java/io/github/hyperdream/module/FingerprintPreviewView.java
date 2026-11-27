package io.github.hyperdream.module;

import android.animation.ValueAnimator;
import android.content.Context;
import com.google.android.material.color.MaterialColors;
import androidx.core.graphics.ColorUtils;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Touch-responsive local artwork only. Never observes biometric or lock-screen events. */
public final class FingerprintPreviewView extends View {
    static final String RIPPLE = "ripple", HALO = "halo", SPARK = "spark";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    private ValueAnimator playback;
    private String effect = RIPPLE;
    private float progress = -1;

    public FingerprintPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    static String sanitize(String value) {
        return HALO.equals(value) || SPARK.equals(value) ? value : RIPPLE;
    }

    public void setEffect(String value) {
        stop(); effect = sanitize(value); invalidate();
    }

    String getEffect() { return effect; }
    boolean isPlaying() { return playback != null && playback.isRunning(); }

    public void play() {
        stop();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            progress = 0.4f; invalidate();
            announceForAccessibility(getContext().getString(R.string.fingerprint_still));
            return;
        }
        playback = ValueAnimator.ofFloat(0, 1);
        playback.setDuration(1000);
        playback.addUpdateListener(frame -> { progress = (float) frame.getAnimatedValue(); invalidate(); });
        playback.start();
    }

    public void stop() {
        if (playback != null) { playback.cancel(); playback = null; }
        progress = -1; invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) { return false; }
        // Native View handles touch slop, cancellation, keyboard and accessibility clicks.
        return super.onTouchEvent(event);
    }

    @Override public boolean performClick() {
        super.performClick(); play(); return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
    }

    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float unit = Math.min(getWidth() / 240f, getHeight() / 220f);
        if (unit <= 0) { return; }
        paint.setStyle(Paint.Style.FILL); paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0));
        ring.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(ring, 24 * unit, 24 * unit, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
        if (progress >= 0 && progress < 1) {
            if (SPARK.equals(effect)) {
                paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, 0));
                paint.setStrokeWidth(3 * unit); paint.setAlpha((int) ((1 - progress) * 230));
                for (int i = 0; i < 16; i++) {
                    double angle = i * Math.PI / 8 + progress * 0.2;
                    float r = (36 + progress * 48) * unit;
                    canvas.drawLine(cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r,
                            cx + (float) Math.cos(angle) * (r + 12 * unit), cy + (float) Math.sin(angle) * (r + 12 * unit), paint);
                }
            } else {
                int count = HALO.equals(effect) ? 1 : 3;
                for (int i = 0; i < count; i++) {
                    float phase = progress - i * 0.16f;
                    if (phase < 0) { continue; }
                    paint.setColor(MaterialColors.getColor(this, HALO.equals(effect) ? com.google.android.material.R.attr.colorSecondary : com.google.android.material.R.attr.colorPrimary, 0));
                    paint.setAlpha((int) (Math.max(0, 1 - phase) * 190));
                    paint.setStrokeWidth((HALO.equals(effect) ? 12 : 2.5f) * unit);
                    canvas.drawCircle(cx, cy, (32 + phase * 60) * unit, paint);
                }
            }
        }
        paint.setAlpha(255); paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0)); paint.setStrokeWidth(2.6f * unit);
        for (int i = 0; i < 5; i++) {
            float r = (8 + i * 5) * unit;
            ring.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(ring, 200 + i * 8, 290 - i * 13, false, paint);
        }
        paint.setColor(ColorUtils.setAlphaComponent(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0), 85)); paint.setStrokeWidth(unit);
        canvas.drawCircle(cx, cy, 35 * unit, paint);
    }
}
