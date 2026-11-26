package io.github.hyperdream.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.animation.TimeInterpolator;
import android.widget.RadioButton;
import android.widget.Switch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Android resource/UI tests, not a substitute for LSPosed or HyperOS device testing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public final class MotionUiTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before
    public void createActivity() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
    }

    @After
    public void destroyActivity() {
        controller.pause().stop().destroy();
    }

    @Test
    public void controlsLoadWithSafeDefaultsAndRestartButton() {
        assertFalse(SettingsStore.read(activity).enabled);
        assertTrue(((RadioButton) activity.findViewById(R.id.curve_system)).isChecked());
        assertFalse(((Switch) activity.findViewById(R.id.scale_delays)).isChecked());
        assertNotNull(activity.findViewById(R.id.restart_launcher));
    }

    @Test
    public void controlsPersistActualSettingsAndResetAllOverrides() {
        ((RadioButton) activity.findViewById(R.id.curve_emphasized)).performClick();
        ((Switch) activity.findViewById(R.id.scale_delays)).performClick();
        ((Switch) activity.findViewById(R.id.enable_duration)).performClick();
        AnimationSettings settings = SettingsStore.read(activity);
        assertTrue(settings.enabled);
        assertTrue(settings.scaleDelays);
        assertEquals("emphasized", settings.curve);
        activity.findViewById(R.id.reset_settings).performClick();
        AnimationSettings reset = SettingsStore.read(activity);
        assertFalse(reset.enabled);
        assertFalse(reset.scaleDelays);
        assertEquals("system", reset.curve);
        assertEquals(1f, reset.durationScale, 0f);
    }

    @Test
    public void curvesHaveValidEndpointsAndStayInRange() {
        assertNull(MotionCurves.create("system"));
        for (String name : new String[]{"smooth", "emphasized", "linear"}) {
            TimeInterpolator curve = MotionCurves.create(name);
            assertNotNull(curve);
            assertEquals(0f, curve.getInterpolation(0f), 0.001f);
            assertEquals(1f, curve.getInterpolation(1f), 0.001f);
            float previous = 0;
            for (int step = 0; step <= 100; step++) {
                float value = curve.getInterpolation(step / 100f);
                assertTrue(value >= 0 && value <= 1);
                assertTrue(value >= previous);
                previous = value;
            }
        }
    }
}
