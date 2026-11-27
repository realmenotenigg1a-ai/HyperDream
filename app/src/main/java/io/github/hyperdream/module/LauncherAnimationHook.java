package io.github.hyperdream.module;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Launcher-local public Android API hooks, not private HyperOS transition hooks. */
final class LauncherAnimationHook {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private LauncherAnimationHook() {}

    static void install() {
        // Independent fallbacks: failure to hook attach must not prevent onCreate/late entry.
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!param.hasThrowable()) { start((Context) param.args[0]); }
                        }
                    });
        } catch (RuntimeException | LinkageError unavailable) {
            XposedBridge.log("[HyperDream] attach callback unavailable: " + unavailable.getClass().getSimpleName());
        }
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) { start((Application) param.thisObject); }
                }
            });
        } catch (RuntimeException | LinkageError unavailable) {
            XposedBridge.log("[HyperDream] onCreate fallback unavailable: " + unavailable.getClass().getSimpleName());
        }
        // Handles module entry after Application.attach has already completed.
        Application application = AndroidAppHelper.currentApplication();
        if (application != null) { start(application); }
    }

    private static void start(Context context) {
        if (context == null || !INITIALIZED.compareAndSet(false, true)) { return; }
        Handler main = new Handler(Looper.getMainLooper());
        StartupRecovery<Bundle> recovery = new StartupRecovery<>(
                (task, delay) -> main.postDelayed(task, delay),
                () -> context.getContentResolver().call(
                        Uri.parse("content://" + AnimationSettings.AUTHORITY),
                        AnimationSettings.METHOD_READ, null, null),
                result -> installFromSettings(context, result),
                (stage, attempt, failure) -> {
                    XposedBridge.log("[HyperDream] Startup " + stage.name()
                            + " attempt=" + attempt + " failure=" + failure);
                    StartupReports.send(context, stage, attempt, failure);
                });
        // One serialized main-thread chain. No repeated hook installation or idle polling.
        main.post(recovery::start);
    }

    private static void installFromSettings(Context context, Bundle result) {
        LauncherSpringHook.install(context, new SpringSettings(
                result.getBoolean(SpringSettings.KEY_ENABLED, false),
                result.getFloat(SpringSettings.KEY_RESPONSE, 1f),
                result.getFloat(SpringSettings.KEY_DAMPING, 1f)));
        AnimationSettings settings = new AnimationSettings(
                result.getBoolean(AnimationSettings.KEY_ENABLED, false),
                result.getFloat(AnimationSettings.KEY_DURATION_SCALE, 1f),
                result.getString(AnimationSettings.KEY_CURVE),
                result.getBoolean(AnimationSettings.KEY_SCALE_DELAYS, false));
        if (!settings.hasOverrides()) {
            XposedBridge.log("[HyperDream] Settings read; generic controls off/original. No generic animator hooks installed.");
            return;
        }
        installAnimationHooks(settings);
    }

    private static void installAnimationHooks(AnimationSettings settings) {
        List<XC_MethodHook.Unhook> installed = new ArrayList<>();
        try {
            if (settings.durationScale != 1f) {
                // Do not also hook AnimatorSet: that would scale child durations twice.
                installed.add(XposedHelpers.findAndHookMethod(ValueAnimator.class,
                        "setDuration", long.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                param.args[0] = settings.scaleDuration((long) param.args[0]);
                            }
                        }));
                if (settings.scaleDelays) {
                    installed.add(XposedHelpers.findAndHookMethod(ValueAnimator.class,
                            "setStartDelay", long.class, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    param.args[0] = settings.scaleStartDelay((long) param.args[0]);
                                }
                            }));
                }
            }
            TimeInterpolator curve = MotionCurves.create(settings.curve);
            if (curve != null) {
                installed.add(XposedHelpers.findAndHookMethod(ValueAnimator.class,
                        "setInterpolator", TimeInterpolator.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                param.args[0] = curve;
                            }
                        }));
            }
        } catch (RuntimeException | LinkageError failure) {
            for (XC_MethodHook.Unhook hook : installed) {
                hook.unhook();
            }
            XposedBridge.log("[HyperDream] Hook registration failed; registered animation hooks rolled back.");
            throw failure;
        }
        XposedBridge.log("[HyperDream] Launcher ValueAnimator hooks registered: duration="
                + settings.durationScale + ", curve=" + settings.curve
                + ", scaleDelays=" + settings.scaleDelays
                + ". Registration is not HyperOS compatibility verification.");
    }
}
