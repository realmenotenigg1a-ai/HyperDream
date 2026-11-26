package io.github.hyperdream.module;

import static org.junit.Assert.*;

import org.junit.Test;

/** Structural fixture tests only; these classes are NOT a real HyperOS launcher. */
public final class SpringParamAdapterTest {
    public static final class Parameter {
        final String key;
        final float damping;
        final float response;
        final float impulse;

        public Parameter(String key, float damping, float response, float impulse) {
            this.key = key;
            this.damping = damping;
            this.response = response;
            this.impulse = impulse;
        }
    }

    public static final class WrongShape {
        final String key = "x";
        final double damping = 1;
        final float response = 1;
        final float impulse = 0;
        public WrongShape(String key, float damping, float response, float impulse) {}
    }

    @Test
    public void producesIndependentTypedCopyWithoutMutatingOriginal() throws Exception {
        Parameter original = new Parameter("width", 0.9f, 0.4f, 2f);
        Parameter[] input = {original};
        Parameter[] result = (Parameter[]) new SpringParamAdapter(Parameter.class)
                .copyWithOverrides(input, new SpringSettings(true, 0.75f, 0.65f));
        assertNotSame(input, result);
        assertNotSame(original, result[0]);
        assertSame(original, input[0]);
        assertEquals("width", result[0].key);
        assertEquals(0.65f, result[0].damping, 0.0001f);
        assertEquals(0.3f, result[0].response, 0.0001f);
        assertEquals(2f, result[0].impulse, 0f);
        assertEquals(0.9f, original.damping, 0f);
        assertEquals(0.4f, original.response, 0f);
    }

    @Test(expected = NoSuchFieldException.class)
    public void rejectsUnrecognizedFieldTypeBeforeHooking() throws Exception {
        new SpringParamAdapter(WrongShape.class);
    }

    @Test
    public void malformedLaterElementDoesNotPartiallyMutateBundle() throws Exception {
        Parameter first = new Parameter("x", 1, 0.4f, 0);
        Parameter[] input = {first, null};
        try {
            new SpringParamAdapter(Parameter.class).copyWithOverrides(input, new SpringSettings(true, 0.5f, 0.7f));
            fail("Expected malformed input rejection");
        } catch (IllegalArgumentException expected) {
            assertSame(first, input[0]);
            assertNull(input[1]);
            assertEquals(0.4f, first.response, 0f);
        }
    }

    @Test
    public void rejectsInvalidTypesSizesAndValues() throws Exception {
        SpringParamAdapter adapter = new SpringParamAdapter(Parameter.class);
        SpringSettings settings = new SpringSettings(true, 1, 1);
        Object[] invalid = {null, new Object[1], new Parameter[0], new Parameter[65],
                new Parameter[]{new Parameter("x", Float.NaN, 1, 0)},
                new Parameter[]{new Parameter("x", 1, -1, 0)}};
        for (Object input : invalid) {
            try {
                adapter.copyWithOverrides(input, settings);
                fail("Expected invalid bundle rejection");
            } catch (IllegalArgumentException expected) {
                // Fail closed before a replacement is returned.
            }
        }
    }

    @Test
    public void disabledAdapterReturnsOriginalAndLimitsAnimationTypes() throws Exception {
        Parameter[] input = {new Parameter("x", 1, 1, 0)};
        assertSame(input, new SpringParamAdapter(Parameter.class)
                .copyWithOverrides(input, new SpringSettings(false, 0.5f, 0.6f)));
        assertTrue(SpringParamAdapter.supportsAnimation("OPEN_FROM_HOME"));
        assertTrue(SpringParamAdapter.supportsAnimation("CLOSE_TO_HOME"));
        assertFalse(SpringParamAdapter.supportsAnimation("RECENTS"));
        assertFalse(SpringParamAdapter.supportsAnimation(null));
    }

    @Test
    public void settingsRejectNonfiniteAndOutOfRangeInput() {
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1, 0, 100}) {
            SpringSettings settings = new SpringSettings(true, invalid, invalid);
            assertEquals(1f, settings.responseMultiplier, 0f);
            assertEquals(1f, settings.damping, 0f);
        }
    }
}
