package io.github.hyperdream.module;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** User-visible build information only; no serials, accounts, or automatic uploads. */
final class DeviceDiagnostics {
    private DeviceDiagnostics() {}

    static String describe(Context context) {
        String launcher;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(TargetPackages.MIUI_HOME, 0);
            launcher = info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException unavailable) {
            launcher = context.getString(R.string.launcher_not_installed);
        }
        return context.getString(R.string.device_report, Build.MANUFACTURER, Build.MODEL,
                Build.VERSION.RELEASE, Integer.toString(Build.VERSION.SDK_INT),
                Build.VERSION.INCREMENTAL, launcher);
    }
}
