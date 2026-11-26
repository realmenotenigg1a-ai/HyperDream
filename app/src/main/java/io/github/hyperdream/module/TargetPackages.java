package io.github.hyperdream.module;

/** Narrow allowlist shared by the entry point and JVM tests. */
final class TargetPackages {
    static final String MIUI_HOME = "com.miui.home";

    private TargetPackages() {}

    static boolean isLauncherMainProcess(String packageName, String processName) {
        return MIUI_HOME.equals(packageName) && MIUI_HOME.equals(processName);
    }
}
