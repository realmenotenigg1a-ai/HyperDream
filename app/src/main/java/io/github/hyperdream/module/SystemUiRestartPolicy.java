package io.github.hyperdream.module;

import java.util.Locale;

/** Fixed, exact-process termination request. Android, not this app, restarts persistent System UI. */
final class SystemUiRestartPolicy {
    static final String PACKAGE = "com.android.systemui";
    static final String COMPLETION_MARKER = "HYPERDREAM_SYSTEMUI_RESTART_REQUESTED";
    static final String COMMAND = "test \"$(id -u)\" = 0 || exit 77; "
            + "test \"$(am get-current-user)\" = 0 || exit 78; "
            + "pids=$(pidof com.android.systemui) || exit 79; "
            + "test -n \"$pids\" || exit 79; "
            + "for p in $pids; do "
            + "case \"$p\" in ''|*[!0-9]*) exit 80;; esac; "
            + "name=$(tr '\\000' '\\n' < /proc/$p/cmdline | head -n 1); "
            + "test \"$name\" = com.android.systemui || exit 81; "
            + "uid=$(awk '/^Uid:/{print $2}' /proc/$p/status); "
            + "case \"$uid\" in ''|*[!0-9]*) exit 82;; esac; "
            + "test \"$uid\" -lt 100000 || exit 83; "
            + "done; "
            + "for p in $pids; do kill -TERM \"$p\" || exit 84; done; "
            + "echo " + COMPLETION_MARKER;

    private SystemUiRestartPolicy() {}

    static boolean requestCompleted(int code, String output) {
        if (code != 0 || output == null || !output.contains(COMPLETION_MARKER)) { return false; }
        String normalized = output.toLowerCase(Locale.ROOT);
        return !normalized.contains("error:") && !normalized.contains("exception");
    }
}
