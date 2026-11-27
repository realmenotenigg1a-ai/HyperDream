package io.github.hyperdream.module;

import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import com.google.android.material.color.MaterialColors;
import androidx.core.graphics.ColorUtils;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Local illustration only: never reports or modifies actual launcher animation state. */
public final class MotionPreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private AnimatorSet playback;
    private float progress;

    public MotionPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void play(float durationScale, String curve, boolean scaleDelays) {
        stop();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            progress = 1f;
            invalidate();
            announceForAccessibility(getContext().getString(R.string.preview_reduced_motion));
            return;
        }
        AnimationSettings previewSettings = new AnimationSettings(true, durationScale, curve, scaleDelays);
        TimeInterpolator selectedCurve = MotionCurves.create(previewSettings.curve);
        ValueAnimator opening = ValueAnimator.ofFloat(0f, 1f);
        opening.setDuration(previewSettings.scaleDuration(480));
        opening.setInterpolator(selectedCurve != null ? selectedCurve
                : new PathInterpolator(0.16f, 1f, 0.3f, 1f));
        opening.addUpdateListener(animator -> updateProgress((float) animator.getAnimatedValue()));
        ValueAnimator closing = ValueAnimator.ofFloat(1f, 0f);
        closing.setStartDelay(previewSettings.scaleStartDelay(400));
        closing.setDuration(previewSettings.scaleDuration(360));
        closing.setInterpolator(selectedCurve != null ? selectedCurve
                : new PathInterpolator(0.4f, 0f, 0.8f, 1f));
        closing.addUpdateListener(animator -> updateProgress((float) animator.getAnimatedValue()));
        playback = new AnimatorSet();
        playback.playSequentially(opening, closing);
        playback.start();
    }

    public void stop() {
        if (playback != null) {
            playback.cancel();
            playback = null;
        }
        updateProgress(0f);
    }

    private void updateProgress(float value) {
        progress = value;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float width = getWidth();
        float height = getHeight();
        float unit = Math.min(width / 300f, height / 180f);
        float inset = 16f * unit;
        paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0));
        rect.set(0, 0, width, height);
        canvas.drawRoundRect(rect, 22 * unit, 22 * unit, paint);
        paint.setShader(null);

        // A deliberately abstract desktop, not a reproduction of a supported launcher layout.
        paint.setColor(ColorUtils.setAlphaComponent(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0), 45));
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 4; column++) {
                float x = inset + column * (width - inset * 2 - 30 * unit) / 3;
                float y = inset + row * 45 * unit;
                rect.set(x, y, x + 30 * unit, y + 30 * unit);
                canvas.drawRoundRect(rect, 10 * unit, 10 * unit, paint);
            }
        }
        float iconSize = 36 * unit;
        float iconTop = height - inset - iconSize;
        float right = mix(inset + iconSize, width - inset, progress);
        float top = mix(iconTop, inset, progress);
        float bottom = height - inset;
        float radius = mix(12 * unit, 18 * unit, progress);
        rect.set(inset, top, right, bottom);
        paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0));
        canvas.drawRoundRect(rect, radius, radius, paint);

        // Inner content appears only when there is room for it.
        float contentAlpha = Math.max(0, (progress - 0.35f) / 0.65f);
        if (contentAlpha > 0) {
            canvas.save();
            canvas.clipRect(inset, top, right, bottom);
            paint.setColor(ColorUtils.setAlphaComponent(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, 0), (int) (230 * contentAlpha)));
            for (int row = 0; row < 3; row++) {
                float y = top + (26 + row * 25) * unit;
                float barWidth = (right - inset - 32 * unit) * (row == 0 ? 0.62f : 0.88f);
                rect.set(inset + 16 * unit, y, inset + 16 * unit + barWidth, y + 8 * unit);
                canvas.drawRoundRect(rect, 4 * unit, 4 * unit, paint);
            }
            canvas.restore();
        }
        paint.setColor(ColorUtils.setAlphaComponent(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0), 155));
        rect.set(width * 0.4f, height - 7 * unit, width * 0.6f, height - 4 * unit);
        canvas.drawRoundRect(rect, 2 * unit, 2 * unit, paint);
    }

    private static float mix(float start, float end, float fraction) {
        return start + (end - start) * fraction;
    }
}
