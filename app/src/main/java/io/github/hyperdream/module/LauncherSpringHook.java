package io.github.hyperdream.module;

import android.content.Context;
import android.graphics.RectF;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Strict opt-in adapter for the known Java SpringParamsProvider API, not native launchers. */
final class LauncherSpringHook {
    private LauncherSpringHook() {}

    static void install(Context context, SpringSettings settings) {
        if (!settings.enabled) {
            HookReports.send(context, HookReports.Status.DISABLED, settings);
            return;
        }
        try {
            ClassLoader loader = context.getClassLoader();
            String prefix = "com.miui.home.recents.util.";
            Class<?> parameter = Class.forName(prefix + "SpringParamsProvider$SpringParam", false, loader);
            Class<?> companion = Class.forName(prefix + "SpringParamsProvider$Companion", false, loader);
            Class<?> animationType = Class.forName(prefix + "RectFSpringAnim$AnimType", false, loader);
            Method target = companion.getDeclaredMethod("generateSpringBundlesByAnimType",
                    animationType, RectF.class, RectF.class);
            if (!animationType.isEnum()
                    || target.getReturnType() != Array.newInstance(parameter, 0).getClass()) {
                throw new NoSuchMethodException("Spring provider signature mismatch");
            }
            SpringParamAdapter adapter = new SpringParamAdapter(parameter);
            SpringCallback callback = new SpringCallback(context, settings, adapter);
            target.setAccessible(true);
            synchronized (callback) {
                XposedBridge.hookMethod(target, callback);
                // Keep REGISTERED before any APPLIED/FAILED report even across launcher threads.
                XposedBridge.log("[HyperDream] Java spring provider hook registered; device effects unverified.");
                HookReports.send(context, HookReports.Status.REGISTERED, settings);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unsupported) {
            XposedBridge.log("[HyperDream] Java spring API unavailable: "
                    + unsupported.getClass().getSimpleName());
            HookReports.send(context, HookReports.Status.UNSUPPORTED, settings);
        }
    }

    private static final class SpringCallback extends XC_MethodHook {
        private final Context context;
        private final SpringSettings settings;
        private final SpringParamAdapter adapter;
        private boolean applied;
        private boolean failed;

        SpringCallback(Context context, SpringSettings settings, SpringParamAdapter adapter) {
            this.context = context;
            this.settings = settings;
            this.adapter = adapter;
        }

        @Override
        protected synchronized void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable() || failed || !(param.args[0] instanceof Enum<?>)) {
                return;
            }
            String type = ((Enum<?>) param.args[0]).name();
            if (!SpringParamAdapter.supportsAnimation(type)) {
                return;
            }
            try {
                Object replacement = adapter.copyWithOverrides(param.getResult(), settings);
                param.setResult(replacement);
                if (!applied) {
                    applied = true;
                    XposedBridge.log("[HyperDream] Spring parameters replaced for " + type);
                    HookReports.send(context, HookReports.Status.APPLIED, settings);
                }
            } catch (ReflectiveOperationException | RuntimeException failure) {
                // Stop touching parameters in this process after unexpected data.
                failed = true;
                XposedBridge.log("[HyperDream] Spring adapter disabled: "
                        + failure.getClass().getSimpleName());
                HookReports.send(context, HookReports.Status.FAILED, settings);
            }
        }
    }
}
