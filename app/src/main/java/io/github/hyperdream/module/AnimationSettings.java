package io.github.hyperdream.module;

/** Validated, immutable settings shared by the UI, provider, and hook. */
final class AnimationSettings {
    static final String PREFERENCES = "animation_settings";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_DURATION_SCALE = "duration_scale";
    static final String AUTHORITY = "io.github.hyperdream.module.settings";
    static final String METHOD_READ = "read_settings";

    final boolean enabled;
    final float durationScale;

    AnimationSettings(boolean enabled, float durationScale) {
        this.enabled = enabled;
        this.durationScale = sanitizeScale(durationScale);
    }

    static float sanitizeScale(float scale) {
        // Only expose tested input presets, not arbitrary or corrupted values.
        if (scale == 0.5f || scale == 0.75f || scale == 1f || scale == 1.5f) {
            return scale;
        }
        return 1f;
    }

    long scaleDuration(long duration) {
        // Leave invalid negative values for Android to reject; preserve instant animations.
        if (!enabled || duration <= 0 || durationScale == 1f) {
            return duration;
        }
        return Math.max(1L, Math.round(duration * (double) durationScale));
    }
}
