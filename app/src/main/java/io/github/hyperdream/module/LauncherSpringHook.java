package io.github.hyperdream.module;

import android.content.Context;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Strict opt-in Java spring adapter. Does not intercept Flutter/Dart or native transitions. */
final class LauncherSpringHook {
    private LauncherSpringHook() {}

    static void install(Context context, SpringSettings settings) {
        if (!settings.enabled) {
            HookReports.send(context, HookReports.Status.DISABLED, settings);
            return;
        }
        String stage = "CLASS_LOOKUP";
        try {
            ClassLoader loader = context.getClassLoader();
            String prefix = "com.miui.home.recents.util.";
            Class<?> parameter = Class.forName(prefix + "SpringParamsProvider$SpringParam", false, loader);
            Class<?> companion = Class.forName(prefix + "SpringParamsProvider$Companion", false, loader);
            Class<?> animationType = Class.forName(prefix + "RectFSpringAnim$AnimType", false, loader);
            stage = "SIGNATURE_VALIDATION";
            Method target = companion.getDeclaredMethod("generateSpringBundlesByAnimType",
                    animationType, RectF.class, RectF.class);
            if (!animationType.isEnum()
                    || target.getReturnType() != Array.newInstance(parameter, 0).getClass()) {
                throw new NoSuchMethodException("Spring provider signature mismatch");
            }
            SpringParamAdapter adapter = new SpringParamAdapter(parameter);
            SpringCallback callback = new SpringCallback(context, settings, adapter,
                    describeAvailableTypes(animationType));
            target.setAccessible(true);
            stage = "HOOK_REGISTRATION";
            synchronized (callback) {
                XposedBridge.hookMethod(target, callback);
                callback.publish();
            }
            // Two bounded snapshots, not a poller. Explicitly captures a method that is never called.
            Handler main = new Handler(Looper.getMainLooper());
            main.postDelayed(callback::publish, 15_000);
            main.postDelayed(callback::publish, 60_000);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unsupported) {
            String detail = "Installation stage: " + stage + "\nFailure class: "
                    + unsupported.getClass().getSimpleName();
            XposedBridge.log("[HyperDream] Java spring API unavailable: " + detail);
            HookReports.send(context, HookReports.Status.UNSUPPORTED, settings, detail);
        }
    }

    private static String describeAvailableTypes(Class<?> animationType) {
        StringBuilder result = new StringBuilder("Declared enum constants: ");
        Object[] constants = animationType.getEnumConstants();
        if (constants != null) {
            for (int i = 0; i < Math.min(constants.length, 16); i++) {
                String name = SpringCallDiagnostics.safeName(((Enum<?>) constants[i]).name());
                if (result.length() + name.length() + 2 > 400) { break; }
                if (i > 0) { result.append(", "); }
                result.append(name);
            }
        }
        return result.toString();
    }

    private static final class SpringCallback extends XC_MethodHook {
        private final Context context;
        private final SpringSettings settings;
        private final SpringParamAdapter adapter;
        private final String availableTypes;
        private final SpringCallDiagnostics diagnostics = new SpringCallDiagnostics();
        private boolean observed;
        private boolean applied;
        private boolean failed;

        SpringCallback(Context context, SpringSettings settings, SpringParamAdapter adapter,
                String availableTypes) {
            this.context = context;
            this.settings = settings;
            this.adapter = adapter;
            this.availableTypes = availableTypes;
        }

        @Override
        protected synchronized void afterHookedMethod(MethodHookParam param) {
            observed = true;
            String type = param.args != null && param.args.length > 0 && param.args[0] instanceof Enum<?>
                    ? ((Enum<?>) param.args[0]).name() : null;
            SpringCallDiagnostics.Outcome outcome;
            boolean importantChange = false;
            if (param.hasThrowable()) {
                outcome = SpringCallDiagnostics.Outcome.ORIGINAL_EXCEPTION;
            } else if (failed) {
                outcome = SpringCallDiagnostics.Outcome.ADAPTER_STOPPED;
            } else if (type == null) {
                outcome = SpringCallDiagnostics.Outcome.INVALID_ARGUMENT;
            } else if (!SpringParamAdapter.supportsAnimation(type)) {
                outcome = SpringCallDiagnostics.Outcome.FILTERED_TYPE;
            } else {
                Object original = param.getResult();
                if (original == null || (original.getClass().isArray() && Array.getLength(original) == 0)) {
                    // An absent bundle is not corruption: do not permanently disable the adapter.
                    outcome = SpringCallDiagnostics.Outcome.EMPTY_RESULT;
                } else {
                    try {
                        Object replacement = adapter.copyWithOverrides(original, settings);
                        param.setResult(replacement);
                        importantChange = !applied;
                        applied = true;
                        outcome = SpringCallDiagnostics.Outcome.REPLACED;
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        failed = true;
                        importantChange = true;
                        outcome = SpringCallDiagnostics.Outcome.ADAPTER_FAILED;
                        XposedBridge.log("[HyperDream] Spring adapter stopped: "
                                + failure.getClass().getSimpleName());
                    }
                }
            }
            boolean sample = diagnostics.record(type, outcome);
            if (sample || importantChange) { publish(); }
        }

        synchronized void publish() {
            HookReports.Status status = failed ? HookReports.Status.FAILED
                    : applied ? HookReports.Status.APPLIED
                    : observed ? HookReports.Status.OBSERVED : HookReports.Status.REGISTERED;
            String detail = availableTypes + "\n" + diagnostics.summary();
            XposedBridge.log("[HyperDream] Spring diagnostic snapshot: " + detail);
            HookReports.send(context, status, settings, detail);
        }
    }
}
