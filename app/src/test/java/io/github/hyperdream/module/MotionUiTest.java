package io.github.hyperdream.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;

import android.animation.TimeInterpolator;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.SeekBar;
import android.widget.RadioGroup;
import android.view.View;
import android.os.Bundle;
import android.content.pm.ProviderInfo;

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

    @Test
    public void springSlidersPersistAndResetIndependently() {
        ((SeekBar) activity.findViewById(R.id.spring_response)).setProgress(5);
        ((SeekBar) activity.findViewById(R.id.spring_damping)).setProgress(3);
        ((Switch) activity.findViewById(R.id.enable_spring)).performClick();
        SpringSettings settings = SettingsStore.readSpring(activity);
        assertTrue(settings.enabled);
        assertEquals(0.75f, settings.responseMultiplier, 0.001f);
        assertEquals(0.65f, settings.damping, 0.001f);
        assertFalse(SettingsStore.read(activity).enabled);
        activity.findViewById(R.id.reset_spring).performClick();
        assertFalse(SettingsStore.readSpring(activity).enabled);
        assertEquals(1f, SettingsStore.readSpring(activity).damping, 0f);
    }

    @Test
    public void tabsExposeRealPagesAndBuildIdentity() {
        assertEquals(View.VISIBLE, activity.findViewById(R.id.page_spring).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.page_setup).getVisibility());
        ((LiquidGlassNavigationView) activity.findViewById(R.id.glass_navigation))
                .selectTab(LiquidGlassNavigationView.DEVICE, false);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.page_setup).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.page_spring).getVisibility());
        String identity = ((android.widget.TextView) activity.findViewById(R.id.build_identity)).getText().toString();
        assertTrue(identity.contains(BuildConfig.VERSION_NAME));
        assertTrue(identity.contains(BuildConfig.BUILD_ID));
    }

    @Test
    public void providerReadsSpringSettingsButRejectsAppForgedLauncherReports() {
        SettingsProvider provider = new SettingsProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = AnimationSettings.AUTHORITY;
        provider.attachInfo(activity, info);
        Bundle snapshot = provider.call(AnimationSettings.METHOD_READ, null, null);
        assertFalse(snapshot.getBoolean(SpringSettings.KEY_ENABLED));
        assertEquals(1f, snapshot.getFloat(SpringSettings.KEY_RESPONSE), 0f);
        try {
            provider.call(HookReports.METHOD_REPORT, null, new Bundle());
            org.junit.Assert.fail("The settings app UID must not impersonate the launcher");
        } catch (SecurityException expected) {
            // Only the real launcher UID can submit cross-process reports.
        }
    }

    @Test
    public void historicalReportDistinguishesRegistrationFromParameterReplacement() {
        Bundle report = new Bundle();
        report.putString("status", "REGISTERED");
        report.putString("build", BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_ID);
        report.putString("snapshot", new SpringSettings(false, 1, 1).snapshot());
        HookReports.record(activity, report);
        assertTrue(HookReports.describe(activity).contains(activity.getString(R.string.report_registered)));
        assertTrue(HookReports.describe(activity).contains(activity.getString(R.string.report_historical)));
        report.putString("status", "APPLIED");
        HookReports.record(activity, report);
        assertTrue(HookReports.describe(activity).contains(activity.getString(R.string.report_applied)));
        SettingsStore.writeSpring(activity, new SpringSettings(true, 0.75f, 0.7f));
        assertTrue(HookReports.describe(activity).contains(activity.getString(R.string.report_stale)));
    }

    @Test
    public void observedStatusIncludesCopyableInvocationEvidence() {
        Bundle report = new Bundle();
        report.putString("status", "OBSERVED");
        report.putString("build", BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_ID);
        report.putString("snapshot", new SpringSettings(false, 1, 1).snapshot());
        report.putString("diagnostics", "Provider calls: 3; filtered: OPEN_FROM_RECENTS");
        HookReports.record(activity, report);
        String description = HookReports.describe(activity);
        assertTrue(description.contains(activity.getString(R.string.report_observed)));
        assertTrue(description.contains("OPEN_FROM_RECENTS"));
        assertNotNull(activity.findViewById(R.id.runtime_details));
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversizedDiagnosticsAreRejectedWithoutChangingSettings() {
        Bundle report = new Bundle();
        report.putString("status", "OBSERVED");
        report.putString("diagnostics", "x".repeat(2049));
        HookReports.record(activity, report);
    }

    @Test
    public void screenUsesPlainBlackBackgroundWithoutDecorativeBackdrop() {
        android.graphics.drawable.Drawable background = activity.findViewById(R.id.screen_root).getBackground();
        assertTrue(background instanceof android.graphics.drawable.ColorDrawable);
        assertEquals(android.graphics.Color.BLACK,
                ((android.graphics.drawable.ColorDrawable) background).getColor());
    }

    @Test
    public void springReportShortcutCopiesBuildAndLatestHookEvidence() {
        Bundle report = new Bundle();
        report.putString("status", "OBSERVED");
        report.putString("build", BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_ID);
        report.putString("snapshot", new SpringSettings(false, 1, 1).snapshot());
        report.putString("diagnostics", "Provider calls observed: 5");
        HookReports.record(activity, report);
        activity.findViewById(R.id.copy_hook_diagnostics).performClick();
        android.content.ClipboardManager clipboard = activity.getSystemService(android.content.ClipboardManager.class);
        assertNotNull(clipboard.getPrimaryClip());
        String copied = clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity).toString();
        assertTrue(copied.contains(BuildConfig.VERSION_NAME));
        assertTrue(copied.contains(BuildConfig.BUILD_ID));
        assertTrue(copied.contains("Provider calls observed: 5"));
    }

    @Test
    public void glassNavigationIsASiblingOverlayWithScrollClearance() {
        View root = activity.findViewById(R.id.screen_root);
        View scroll = activity.findViewById(R.id.studio_scroll);
        View navigation = activity.findViewById(R.id.glass_navigation);
        assertSame(root, navigation.getParent());
        assertSame(root, scroll.getParent());
        assertTrue(scroll.getPaddingBottom() >= 106 * activity.getResources().getDisplayMetrics().density);
    }

    @Test
    public void draggingGlassFarOutsideBarKeepsLensInsideAndSelectsDestination() {
        LiquidGlassNavigationView navigation = activity.findViewById(R.id.glass_navigation);
        navigation.layout(0, 0, 360, 90);
        long time = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(time, time,
                android.view.MotionEvent.ACTION_DOWN, navigation.lensCenterX(), navigation.lensCenterY(), 0);
        navigation.onTouchEvent(down); down.recycle();
        for (float[] position : new float[][]{{-10000, -10000}, {10000, 10000}, {10000, 45}}) {
            android.view.MotionEvent move = android.view.MotionEvent.obtain(time, time + 20,
                    android.view.MotionEvent.ACTION_MOVE, position[0], position[1], 0);
            navigation.onTouchEvent(move); move.recycle();
            assertTrue(navigation.isLensContained());
        }
        android.view.MotionEvent up = android.view.MotionEvent.obtain(time, time + 40,
                android.view.MotionEvent.ACTION_UP, 10000, 45, 0);
        navigation.onTouchEvent(up); up.recycle();
        assertEquals(LiquidGlassNavigationView.DEVICE, navigation.getSelectedTab());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.page_setup).getVisibility());
        navigation.pauseGlass();
        assertTrue(navigation.isLensContained());
    }

    @Test
    public void virtualNavigationButtonsAreLabeledAndActionable() {
        LiquidGlassNavigationView navigation = activity.findViewById(R.id.glass_navigation);
        android.view.accessibility.AccessibilityNodeProvider provider = navigation.getAccessibilityNodeProvider();
        android.view.accessibility.AccessibilityNodeInfo device = provider.createAccessibilityNodeInfo(LiquidGlassNavigationView.DEVICE);
        assertEquals(activity.getString(R.string.tab_device), device.getText().toString());
        assertTrue(device.isClickable());
        assertTrue(provider.performAction(LiquidGlassNavigationView.DEVICE, android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null));
        assertEquals(LiquidGlassNavigationView.DEVICE, navigation.getSelectedTab());
        assertFalse(provider.performAction(99, android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null));
    }

    @Test
    @Config(sdk = 32)
    public void android12UsesSafeBitmapFallbackAndCanNavigate() {
        LiquidGlassNavigationView navigation = activity.findViewById(R.id.glass_navigation);
        navigation.layout(0, 0, 360, 90);
        navigation.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(360, 90,
                android.graphics.Bitmap.Config.ARGB_8888)));
        navigation.selectTab(LiquidGlassNavigationView.ANIMATOR, false);
        assertTrue(navigation.isLensContained());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.page_motion).getVisibility());
    }

    @Test
    public void floatingDestinationSurvivesActivityRecreation() {
        ((LiquidGlassNavigationView) activity.findViewById(R.id.glass_navigation))
                .selectTab(LiquidGlassNavigationView.DEVICE, false);
        controller.recreate();
        activity = controller.get();
        assertEquals(LiquidGlassNavigationView.DEVICE,
                ((LiquidGlassNavigationView) activity.findViewById(R.id.glass_navigation)).getSelectedTab());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.page_setup).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.page_motion).getVisibility());
    }

    @Test(expected = IllegalArgumentException.class)
    public void capturingAnAncestorIsRejectedToPreventRecursiveScreenshots() {
        ((LiquidGlassNavigationView) activity.findViewById(R.id.glass_navigation))
                .setBackdropSource(activity.findViewById(R.id.screen_root));
    }

    @Test
    public void startupCheckpointIsCopyableButNotAnAppliedHookClaim() {
        Bundle report = new Bundle();
        report.putString("stage", "SETTINGS_UNAVAILABLE");
        report.putInt("attempt", 6);
        report.putString("failure", "NullSettings");
        report.putString("build", BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_ID);
        StartupReports.record(activity, report);
        activity.findViewById(R.id.copy_hook_diagnostics).performClick();
        android.content.ClipboardManager clipboard = activity.getSystemService(android.content.ClipboardManager.class);
        String copied = clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity).toString();
        assertTrue(copied.contains("SETTINGS_UNAVAILABLE"));
        assertTrue(copied.contains("NullSettings"));
        assertTrue(copied.contains(activity.getString(R.string.report_none)));
        assertFalse(copied.contains(activity.getString(R.string.report_applied)));
        report.putString("build", "older-build");
        StartupReports.record(activity, report);
        assertTrue(StartupReports.describe(activity).contains(activity.getString(R.string.startup_stale)));
    }

    @Test
    public void providerRejectsAppForgedStartupCheckpoints() {
        SettingsProvider provider = new SettingsProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = AnimationSettings.AUTHORITY;
        provider.attachInfo(activity, info);
        try {
            provider.call(StartupReports.METHOD_REPORT, null, new Bundle());
            org.junit.Assert.fail("App UID must not impersonate launcher startup");
        } catch (SecurityException expected) { /* Authenticated launcher only. */ }
    }

    @Test(expected = IllegalArgumentException.class)
    public void startupReportsRejectUnboundedAttempts() {
        Bundle report = new Bundle();
        report.putString("stage", "READING_SETTINGS");
        report.putInt("attempt", 99);
        StartupReports.record(activity, report);
    }

    @Test
    public void suppliedLogoIsUsedForAppAndHeader() {
        android.graphics.Bitmap logo = android.graphics.BitmapFactory.decodeResource(
                activity.getResources(), R.drawable.ic_launcher);
        assertEquals(1080, logo.getWidth());
        assertEquals(1080, logo.getHeight());
        assertEquals(R.drawable.ic_launcher, activity.getApplicationInfo().icon);
    }

    @Test
    public void technicalDetailsAreOptInAndDisclosureSurvivesRecreation() {
        assertEquals(View.GONE, activity.findViewById(R.id.diagnostic_details).getVisibility());
        activity.findViewById(R.id.toggle_diagnostics).performClick();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.diagnostic_details).getVisibility());
        assertEquals(activity.getString(R.string.expanded),
                activity.findViewById(R.id.toggle_diagnostics).getStateDescription().toString());
        controller.recreate(); activity = controller.get();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.diagnostic_details).getVisibility());
        activity.findViewById(R.id.toggle_diagnostics).performClick();
        assertEquals(View.GONE, activity.findViewById(R.id.diagnostic_details).getVisibility());
    }

    @Test
    public void restartAndConfigurationStatusLiveInDeviceInsteadOfCoveringStudio() {
        View setup = activity.findViewById(R.id.page_setup);
        android.view.ViewParent parent = activity.findViewById(R.id.restart_launcher).getParent();
        while (parent != null && parent != setup) { parent = parent.getParent(); }
        assertSame(setup, parent);
        ((LiquidGlassNavigationView) activity.findViewById(R.id.glass_navigation))
                .selectTab(LiquidGlassNavigationView.ANIMATOR, false);
        assertEquals(activity.getString(R.string.animator_hero),
                ((android.widget.TextView) activity.findViewById(R.id.studio_heading)).getText().toString());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.page_motion).getVisibility());
        assertEquals(View.GONE, setup.getVisibility());
    }

    @Test
    public void motionFeedbackCoversButtonsSwitchesRadiosAndSlidersWithoutChangingClicks() {
        StudioMotion feedback = new StudioMotion();
        feedback.bind(activity.findViewById(R.id.screen_root));
        feedback.refresh(true);
        for (int id : new int[]{R.id.play_preview, R.id.enable_duration, R.id.duration_fast, R.id.spring_damping}) {
            assertNotNull(activity.findViewById(id).getStateListAnimator());
        }
        ((Switch) activity.findViewById(R.id.enable_duration)).performClick();
        assertTrue(SettingsStore.read(activity).enabled);
        ((RadioButton) activity.findViewById(R.id.duration_fast)).performClick();
        assertEquals(0.5f, SettingsStore.read(activity).durationScale, 0f);
        assertEquals(activity.getString(R.string.saved_locally),
                ((android.widget.TextView) activity.findViewById(R.id.motion_saved)).getText().toString());
        activity.findViewById(R.id.duration_fast).setScaleX(0.9f);
        feedback.refresh(false);
        for (int id : new int[]{R.id.play_preview, R.id.enable_duration, R.id.duration_fast, R.id.spring_damping}) {
            assertNull(activity.findViewById(id).getStateListAnimator());
            assertEquals(1f, activity.findViewById(id).getScaleX(), 0f);
        }
        feedback.stop();
    }

    private void layoutStudio() {
        View root = activity.findViewById(R.id.screen_root);
        root.requestLayout();
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 360, 800);
        root.getViewTreeObserver().dispatchOnPreDraw();
    }

    @Test
    public void eachTabRestoresScrollAfterNewPageMeasurement() {
        activity.findViewById(R.id.toggle_diagnostics).performClick();
        layoutStudio();
        android.widget.ScrollView scroll = activity.findViewById(R.id.studio_scroll);
        LiquidGlassNavigationView navigation = activity.findViewById(R.id.glass_navigation);
        scroll.scrollTo(0, 180);
        int homeOffset = scroll.getScrollY();
        assertTrue(homeOffset > 0);
        navigation.selectTab(LiquidGlassNavigationView.DEVICE, false);
        layoutStudio();
        assertEquals(0, scroll.getScrollY());
        scroll.scrollTo(0, 60);
        int deviceOffset = scroll.getScrollY();
        assertTrue(deviceOffset > 0);
        navigation.selectTab(LiquidGlassNavigationView.HOME, false);
        layoutStudio();
        assertEquals(homeOffset, scroll.getScrollY());
        navigation.selectTab(LiquidGlassNavigationView.DEVICE, false);
        layoutStudio();
        assertEquals(deviceOffset, scroll.getScrollY());
    }

    @Test
    public void versionOneAndCompactFourDestinationNavigation() {
        assertEquals("1.0", BuildConfig.VERSION_NAME);
        LiquidGlassNavigationView navigation = activity.findViewById(R.id.glass_navigation);
        assertEquals(72 * activity.getResources().getDisplayMetrics().density,
                navigation.getLayoutParams().height, 0.1f);
        assertEquals(4, navigation.getAccessibilityNodeProvider()
                .createAccessibilityNodeInfo(View.NO_ID).getChildCount());
        navigation.selectTab(LiquidGlassNavigationView.FINGERPRINTS, false);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.page_fingerprints).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.page_motion).getVisibility());
        assertEquals(activity.getString(R.string.fingerprint_hero),
                ((android.widget.TextView) activity.findViewById(R.id.studio_heading)).getText().toString());
    }

    @Test
    public void fingerprintStyleIsLocalPersistentAndStopsWhenLeavingPage() {
        LiquidGlassNavigationView navigation = activity.findViewById(R.id.glass_navigation);
        navigation.selectTab(LiquidGlassNavigationView.FINGERPRINTS, false);
        ((RadioButton) activity.findViewById(R.id.fingerprint_spark)).performClick();
        FingerprintPreviewView fingerprint = activity.findViewById(R.id.fingerprint_preview);
        assertEquals(FingerprintPreviewView.SPARK, fingerprint.getEffect());
        assertFalse(SettingsStore.read(activity).enabled);
        assertFalse(SettingsStore.readSpring(activity).enabled);
        fingerprint.performClick();
        navigation.selectTab(LiquidGlassNavigationView.HOME, false);
        assertFalse(fingerprint.isPlaying());
        controller.recreate(); activity = controller.get();
        assertEquals(FingerprintPreviewView.SPARK,
                ((FingerprintPreviewView) activity.findViewById(R.id.fingerprint_preview)).getEffect());
    }

    @Test
    public void animationWarningsAreDirectChildrenNotHiddenInDisclosure() {
        for (int id : new int[]{R.id.page_spring, R.id.page_motion}) {
            android.view.ViewGroup page = activity.findViewById(id);
            android.widget.TextView warning = (android.widget.TextView) page.getChildAt(0);
            assertEquals(activity.getString(R.string.animations_not_working), warning.getText().toString());
            assertEquals(View.VISIBLE, warning.getVisibility());
        }
    }

    @Test
    public void fingerprintPreviewDrawsEachStyleAndSanitizesUnknownValues() {
        FingerprintPreviewView fingerprint = activity.findViewById(R.id.fingerprint_preview);
        fingerprint.layout(0, 0, 240, 220);
        for (String style : new String[]{"ripple", "halo", "spark", "invalid"}) {
            fingerprint.setEffect(style);
            fingerprint.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(240, 220,
                    android.graphics.Bitmap.Config.ARGB_8888)));
        }
        assertEquals(FingerprintPreviewView.RIPPLE, fingerprint.getEffect());
        fingerprint.stop(); assertFalse(fingerprint.isPlaying());
    }
}
