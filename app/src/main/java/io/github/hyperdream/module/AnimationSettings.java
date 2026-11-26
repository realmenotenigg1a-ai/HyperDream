package io.github.hyperdream.module;

/** Validated, immutable settings shared by the UI, provider, and hooks. */
final class AnimationSettings {
    static final String PREFERENCES = "animation_settings";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_DURATION_SCALE = "duration_scale";
    static final String KEY_CURVE = "curve";
    static final String KEY_SCALE_DELAYS = "scale_delays";
    static final String AUTHORITY = "io.github.hyperdream.module.settings";
    static final String METHOD_READ = "read_settings";
    static final String CURVE_SYSTEM = "system";
    static final String CURVE_SMOOTH = "smooth";
    static final String CURVE_EMPHASIZED = "emphasized";
    static final String CURVE_LINEAR = "linear";

    final boolean enabled;
    final float durationScale;
    final String curve;
    final boolean scaleDelays;

    AnimationSettings(boolean enabled, float durationScale) {
        this(enabled, durationScale, CURVE_SYSTEM, false);
    }

    AnimationSettings(boolean enabled, float durationScale, String curve, boolean scaleDelays) {
        this.enabled = enabled;
        this.durationScale = sanitizeScale(durationScale);
        this.curve = sanitizeCurve(curve);
        this.scaleDelays = scaleDelays;
    }

    static float sanitizeScale(float scale) {
        if (scale == 0.5f || scale == 0.75f || scale == 1f || scale == 1.5f) {
            return scale;
        }
        return 1f;
    }

    static String sanitizeCurve(String curve) {
        if (CURVE_SMOOTH.equals(curve) || CURVE_EMPHASIZED.equals(curve)
                || CURVE_LINEAR.equals(curve)) {
            return curve;
        }
        return CURVE_SYSTEM;
    }

    boolean hasOverrides() {
        return enabled && (durationScale != 1f || !CURVE_SYSTEM.equals(curve));
    }

    long scaleDuration(long duration) {
        // Preserve Android's invalid-value validation and instant animations.
        if (!enabled || duration <= 0 || durationScale == 1f) {
            return duration;
        }
        return Math.max(1L, Math.round(duration * (double) durationScale));
    }

    long scaleStartDelay(long delay) {
        return scaleDelays ? scaleDuration(delay) : delay;
    }
}
