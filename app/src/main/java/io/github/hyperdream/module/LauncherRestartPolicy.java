package io.github.hyperdream.module;

import java.util.Locale;

/** Fixed command only: no package, profile, or shell fragment comes from UI/IPC input. */
final class LauncherRestartPolicy {
    static final String COMPLETION_MARKER = "HYPERDREAM_RESTART_REQUESTED";
    static final String COMMAND = "test \"$(id -u)\" = 0 || exit 77; "
            + "am force-stop --user current com.miui.home && "
            + "am start --user current -W -a android.intent.action.MAIN "
            + "-c android.intent.category.HOME -p com.miui.home && "
            + "echo " + COMPLETION_MARKER;

    private LauncherRestartPolicy() {}

    static boolean requestCompleted(int exitCode, String output) {
        if (exitCode != 0 || output == null || !output.contains(COMPLETION_MARKER)) {
            return false;
        }
        String normalized = output.toLowerCase(Locale.ROOT);
        return !normalized.contains("error:") && !normalized.contains("exception");
    }
}
