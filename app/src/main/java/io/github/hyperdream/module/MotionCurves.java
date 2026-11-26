package io.github.hyperdream.module;

import android.animation.TimeInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

final class MotionCurves {
    private MotionCurves() {}

    /** Null means preserve the caller's interpolator, not substitute a new default. */
    static TimeInterpolator create(String curve) {
        switch (AnimationSettings.sanitizeCurve(curve)) {
            case AnimationSettings.CURVE_SMOOTH:
                return new PathInterpolator(0.4f, 0f, 0.2f, 1f);
            case AnimationSettings.CURVE_EMPHASIZED:
                return new PathInterpolator(0.16f, 1f, 0.3f, 1f);
            case AnimationSettings.CURVE_LINEAR:
                return new LinearInterpolator();
            default:
                return null;
        }
    }
}
