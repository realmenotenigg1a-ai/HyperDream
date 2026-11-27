package io.github.hyperdream.module;

import static org.junit.Assert.*;
import org.junit.Test;

public final class SystemUiRestartPolicyTest {
    @Test public void commandTargetsExactOwnerSystemUiWithoutForceStopOrReboot() {
        String command = SystemUiRestartPolicy.COMMAND;
        assertTrue(command.contains("id -u"));
        assertTrue(command.contains("am get-current-user"));
        assertTrue(command.contains("pidof com.android.systemui"));
        assertTrue(command.contains("/proc/$p/cmdline"));
        assertTrue(command.contains("/proc/$p/status"));
        assertTrue(command.contains("kill -TERM"));
        assertFalse(command.contains("force-stop"));
        assertFalse(command.contains("system_server"));
        assertFalse(command.contains("reboot"));
        assertFalse(command.contains("pm clear"));
    }

    @Test public void successfulCommandIsOnlyARequestNotRecoveryProof() {
        assertTrue(SystemUiRestartPolicy.requestCompleted(0, SystemUiRestartPolicy.COMPLETION_MARKER));
        assertFalse(SystemUiRestartPolicy.requestCompleted(1, SystemUiRestartPolicy.COMPLETION_MARKER));
        assertFalse(SystemUiRestartPolicy.requestCompleted(0, ""));
        assertFalse(SystemUiRestartPolicy.requestCompleted(0, null));
        assertFalse(SystemUiRestartPolicy.requestCompleted(0, "Error: " + SystemUiRestartPolicy.COMPLETION_MARKER));
    }

    @Test public void restartGateRejectsBothActionsBeforeAnyRootWork() {
        RestartGuard.RUNNING.set(true);
        try {
            assertEquals(SystemUiRestarter.Result.BUSY, SystemUiRestarter.restart(null));
            assertEquals(LauncherRestarter.Result.BUSY, LauncherRestarter.restart(null));
        } finally { RestartGuard.RUNNING.set(false); }
    }

    @Test public void gateNotifiesActiveUiOnCompletionAndCanUnsubscribe() {
        java.util.List<Boolean> states = new java.util.ArrayList<>();
        Runnable observer = () -> states.add(RestartGuard.RUNNING.get());
        RestartGuard.observe(observer);
        try {
            assertTrue(RestartGuard.begin());
            assertFalse(RestartGuard.begin());
            RestartGuard.release();
            assertEquals(java.util.List.of(false, true, false), states);
        } finally {
            RestartGuard.removeObserver(observer);
            RestartGuard.release();
        }
        assertEquals(3, states.size());
    }
}
