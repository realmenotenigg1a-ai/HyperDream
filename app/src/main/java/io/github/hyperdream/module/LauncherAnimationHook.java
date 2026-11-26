package io.github.hyperdream.module;

import android.animation.ValueAnimator;
import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Uses a public Android API, not guessed private HyperOS method names. */
final class LauncherAnimationHook {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private LauncherAnimationHook() {}

    static void install() {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable() || !INITIALIZED.compareAndSet(false, true)) {
                            return;
                        }
                        Context context = (Context) param.args[0];
                        try {
                            Bundle result = context.getContentResolver().call(
                                    Uri.parse("content://" + AnimationSettings.AUTHORITY),
                                    AnimationSettings.METHOD_READ, null, null);
                            if (result == null) {
                                XposedBridge.log("[HyperDream] Settings unavailable; no animation hook installed.");
                                return;
                            }
                            AnimationSettings settings = new AnimationSettings(
                                    result.getBoolean(AnimationSettings.KEY_ENABLED, false),
                                    result.getFloat(AnimationSettings.KEY_DURATION_SCALE, 1f));
                            if (!settings.enabled || settings.durationScale == 1f) {
                                XposedBridge.log("[HyperDream] Launcher loaded; duration control off or 1x. No animation hook installed.");
                                return;
                            }
                            installDurationHook(settings);
                        } catch (RuntimeException | LinkageError failure) {
                            // A missing/blocked settings bridge must not prevent launcher startup.
                            XposedBridge.log("[HyperDream] Initialization failed; leaving animations unchanged: "
                                    + failure.getClass().getSimpleName());
                        }
                    }
                });
    }

    private static void installDurationHook(AnimationSettings settings) {
        // Hook only ValueAnimator. Hooking AnimatorSet as well would scale children twice.
        XposedHelpers.findAndHookMethod(ValueAnimator.class, "setDuration", long.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.args[0] = settings.scaleDuration((long) param.args[0]);
                    }
                });
        XposedBridge.log("[HyperDream] Launcher ValueAnimator duration hook installed; scale="
                + settings.durationScale + ". This does not verify HyperOS transition compatibility.");
    }
}
