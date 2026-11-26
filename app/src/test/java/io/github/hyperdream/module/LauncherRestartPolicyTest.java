package io.github.hyperdream.module;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LauncherRestartPolicyTest {
    @Test
    public void fixedCommandTargetsOnlyLauncherWithoutClearingDataOrRebooting() {
        String command = LauncherRestartPolicy.COMMAND;
        assertTrue(command.contains("id -u"));
        assertTrue(command.contains("am force-stop --user current com.miui.home &&"));
        assertTrue(command.contains("-c android.intent.category.HOME -p com.miui.home"));
        assertFalse(command.contains("pm clear"));
        assertFalse(command.contains("killall"));
        assertFalse(command.contains("reboot"));
        assertFalse(command.contains("com.android.systemui"));
    }

    @Test
    public void requiresZeroExitAndCompletionMarker() {
        assertTrue(LauncherRestartPolicy.requestCompleted(0,
                "Status: ok\n" + LauncherRestartPolicy.COMPLETION_MARKER));
        assertFalse(LauncherRestartPolicy.requestCompleted(1, LauncherRestartPolicy.COMPLETION_MARKER));
        assertFalse(LauncherRestartPolicy.requestCompleted(77, "permission denied"));
        assertFalse(LauncherRestartPolicy.requestCompleted(0, ""));
        assertFalse(LauncherRestartPolicy.requestCompleted(0, null));
    }

    @Test
    public void androidErrorOutputDoesNotBecomeFalseSuccess() {
        assertFalse(LauncherRestartPolicy.requestCompleted(0,
                "Error: no matching Home\n" + LauncherRestartPolicy.COMPLETION_MARKER));
        assertFalse(LauncherRestartPolicy.requestCompleted(0,
                "java.lang.SecurityException\n" + LauncherRestartPolicy.COMPLETION_MARKER));
    }
}
