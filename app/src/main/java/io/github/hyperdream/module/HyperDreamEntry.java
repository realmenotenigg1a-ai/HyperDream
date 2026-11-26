package io.github.hyperdream.module;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Diagnostic-only entry point. No launcher methods are hooked yet. */
public final class HyperDreamEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        // Scope in the manager is not a substitute for an in-code allowlist.
        if (!TargetPackages.isLauncherMainProcess(
                loadPackageParam.packageName, loadPackageParam.processName)) {
            return;
        }

        XposedBridge.log("[HyperDream] Loaded in com.miui.home main process; "
                + "diagnostic scaffold only, no animation hooks installed.");
    }
}
