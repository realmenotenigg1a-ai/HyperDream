package io.github.hyperdream.module;

/** Experimental spring overrides, independent of the generic animation controls. */
final class SpringSettings {
    static final String KEY_ENABLED = "spring_enabled";
    static final String KEY_RESPONSE = "spring_response";
    static final String KEY_DAMPING = "spring_damping";

    final boolean enabled;
    final float responseMultiplier;
    final float damping;

    SpringSettings(boolean enabled, float responseMultiplier, float damping) {
        this.enabled = enabled;
        this.responseMultiplier = finiteRange(responseMultiplier, 0.5f, 1.5f, 1f);
        this.damping = finiteRange(damping, 0.5f, 1.2f, 1f);
    }

    private static float finiteRange(float value, float min, float max, float fallback) {
        return Float.isFinite(value) && value >= min && value <= max ? value : fallback;
    }

    float adjustedResponse(float original) {
        if (!Float.isFinite(original) || original <= 0f || original > 10f) {
            throw new IllegalArgumentException("Unexpected spring response");
        }
        float result = original * responseMultiplier;
        if (result < 0.01f || result > 10f) {
            throw new IllegalArgumentException("Spring response outside guarded range");
        }
        return result;
    }

    String snapshot() {
        return enabled + ":" + responseMultiplier + ":" + damping;
    }
}
