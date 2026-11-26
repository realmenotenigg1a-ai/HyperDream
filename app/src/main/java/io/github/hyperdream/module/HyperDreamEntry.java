package io.github.hyperdream.module;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Entry point restricted to the Xiaomi launcher main process. */
public final class HyperDreamEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        // Scope in the manager is not a substitute for an in-code allowlist.
        if (!TargetPackages.isLauncherMainProcess(
                loadPackageParam.packageName, loadPackageParam.processName)) {
            return;
        }

        XposedBridge.log("[HyperDream] Loaded in com.miui.home main process.");
        try {
            LauncherAnimationHook.install();
        } catch (RuntimeException | LinkageError failure) {
            XposedBridge.log("[HyperDream] Could not register initialization callback: "
                    + failure.getClass().getSimpleName());
        }
    }
}
