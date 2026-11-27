package io.github.hyperdream.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AnimationSettingsTest {
    @Test
    public void supportedPresetsScaleDuration() {
        assertEquals(150, new AnimationSettings(true, 0.5f).scaleDuration(300));
        assertEquals(225, new AnimationSettings(true, 0.75f).scaleDuration(300));
        assertEquals(300, new AnimationSettings(true, 1f).scaleDuration(300));
        assertEquals(450, new AnimationSettings(true, 1.5f).scaleDuration(300));
    }

    @Test
    public void disabledPreservesOriginalDuration() {
        assertEquals(300, new AnimationSettings(false, 0.5f).scaleDuration(300));
    }

    @Test
    public void preservesZeroAndInvalidNegativeDurations() {
        AnimationSettings settings = new AnimationSettings(true, 0.5f);
        assertEquals(0, settings.scaleDuration(0));
        assertEquals(-1, settings.scaleDuration(-1));
        assertEquals(Long.MIN_VALUE, settings.scaleDuration(Long.MIN_VALUE));
    }

    @Test
    public void tinyPositiveDurationsNeverBecomeZero() {
        assertEquals(1, new AnimationSettings(true, 0.5f).scaleDuration(1));
    }

    @Test
    public void invalidScalesFallBackToOriginalDuration() {
        float[] invalid = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                0f, -1f, 100f, 0.6f};
        for (float scale : invalid) {
            assertEquals(1f, AnimationSettings.sanitizeScale(scale), 0f);
            assertEquals(300, new AnimationSettings(true, scale).scaleDuration(300));
        }
    }

    @Test
    public void largeDurationsSaturateRatherThanOverflow() {
        assertEquals(Long.MAX_VALUE,
                new AnimationSettings(true, 1.5f).scaleDuration(Long.MAX_VALUE));
    }

    @Test
    public void curveOnlyOverrideWorksAtOriginalDuration() {
        assertTrue(new AnimationSettings(true, 1f, "smooth", false).hasOverrides());
        assertTrue(new AnimationSettings(true, 1f, "emphasized", false).hasOverrides());
        assertTrue(new AnimationSettings(true, 1f, "linear", false).hasOverrides());
        assertFalse(new AnimationSettings(true, 1f, "system", true).hasOverrides());
        assertFalse(new AnimationSettings(false, 0.5f, "smooth", true).hasOverrides());
    }

    @Test
    public void unknownCurvesAndMissingUpgradePreferencesPreserveOriginal() {
        assertEquals("system", AnimationSettings.sanitizeCurve(null));
        assertEquals("system", AnimationSettings.sanitizeCurve("untrusted"));
        AnimationSettings settings = new AnimationSettings(true, 1f);
        assertEquals("system", settings.curve);
        assertFalse(settings.scaleDelays);
        assertFalse(settings.hasOverrides());
    }

    @Test
    public void delaysAreOnlyScaledWhenExplicitlyEnabled() {
        assertEquals(100, new AnimationSettings(true, 0.5f, "system", true).scaleStartDelay(200));
        assertEquals(200, new AnimationSettings(true, 0.5f, "system", false).scaleStartDelay(200));
        assertEquals(200, new AnimationSettings(false, 0.5f, "smooth", true).scaleStartDelay(200));
        assertEquals(0, new AnimationSettings(true, 0.5f, "system", true).scaleStartDelay(0));
        assertEquals(-1, new AnimationSettings(true, 0.5f, "system", true).scaleStartDelay(-1));
    }
}
