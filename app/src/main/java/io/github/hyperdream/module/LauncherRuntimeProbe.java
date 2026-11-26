package io.github.hyperdream.module;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.File;

final class LauncherRuntimeProbe {
    private LauncherRuntimeProbe() {}

    /** Call off the UI thread. Never executes or extracts code from another APK. */
    static String describe(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(TargetPackages.MIUI_HOME, 0);
            String[] splits = info.splitSourceDirs;
            File[] sources = new File[1 + (splits == null ? 0 : splits.length)];
            sources[0] = info.sourceDir == null ? null : new File(info.sourceDir);
            for (int i = 1; i < sources.length; i++) {
                sources[i] = splits[i - 1] == null ? null : new File(splits[i - 1]);
            }
            NativeLibraryProbe.Result result = NativeLibraryProbe.inspect(sources);
            String explanation = context.getString(result.hasFlutterIndicators()
                    ? R.string.runtime_flutter_indicators : R.string.runtime_no_indicators);
            String evidence = result.evidence.isEmpty() ? context.getString(R.string.runtime_no_evidence)
                    : String.join("\n", result.evidence);
            return explanation + "\n\n" + evidence + "\n\n"
                    + context.getString(result.complete ? R.string.runtime_scan_complete : R.string.runtime_scan_partial);
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return context.getString(R.string.runtime_unavailable);
        }
    }
}
