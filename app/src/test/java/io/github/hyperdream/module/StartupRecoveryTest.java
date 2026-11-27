package io.github.hyperdream.module;

import static org.junit.Assert.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.Test;

public final class StartupRecoveryTest {
    private final Queue<Runnable> scheduled = new ArrayDeque<>();
    private final List<Long> delays = new ArrayList<>();
    private final List<StartupRecovery.Stage> stages = new ArrayList<>();
    private int reads;
    private int installs;
    private final StartupRecovery.Scheduler scheduler = (task, delay) -> {
        scheduled.add(task); delays.add(delay);
    };
    private final StartupRecovery.Reporter reporter = (stage, attempt, failure) -> stages.add(stage);

    private void drain() {
        int guard = 0;
        while (!scheduled.isEmpty()) {
            assertTrue(++guard < 20);
            scheduled.remove().run();
        }
    }

    @Test public void successfulReadInstallsExactlyOnceAcrossRepeatedStartupSignals() {
        StartupRecovery<String> recovery = new StartupRecovery<>(scheduler,
                () -> { reads++; return "settings"; }, value -> installs++, reporter);
        recovery.start(); recovery.start(); drain();
        assertEquals(1, reads); assertEquals(1, installs);
        assertEquals(StartupRecovery.Stage.INITIALIZED, stages.get(stages.size() - 1));
        assertTrue(delays.isEmpty());
    }

    @Test public void nullThenUnavailableProviderRecoversWithoutDuplicateHooks() {
        StartupRecovery<String> recovery = new StartupRecovery<>(scheduler, () -> {
            reads++;
            if (reads == 1) { return null; }
            if (reads == 2) { throw new IllegalStateException(); }
            return "settings";
        }, value -> installs++, reporter);
        recovery.start(); drain();
        assertEquals(3, reads); assertEquals(1, installs);
        assertEquals(List.of(1000L, 3000L), delays);
        assertEquals(StartupRecovery.Stage.INITIALIZED, stages.get(stages.size() - 1));
    }

    @Test public void persistentFailureStopsAfterSixReadsAnd56SecondsOfScheduledDelay() {
        StartupRecovery<String> recovery = new StartupRecovery<>(scheduler,
                () -> { reads++; return null; }, value -> installs++, reporter);
        recovery.start(); drain(); recovery.start();
        assertEquals(6, reads); assertEquals(0, installs);
        assertEquals(56000L, delays.stream().mapToLong(Long::longValue).sum());
        assertEquals(StartupRecovery.Stage.SETTINGS_UNAVAILABLE, stages.get(stages.size() - 1));
    }

    @Test public void authorizationDenialDoesNotRetryOrInstall() {
        StartupRecovery<String> recovery = new StartupRecovery<>(scheduler,
                () -> { reads++; throw new SecurityException(); }, value -> installs++, reporter);
        recovery.start(); drain();
        assertEquals(1, reads); assertEquals(0, installs);
        assertEquals(StartupRecovery.Stage.SETTINGS_DENIED, stages.get(stages.size() - 1));
        assertTrue(delays.isEmpty());
    }

    @Test public void partialInstallFailureIsNeverRetried() {
        StartupRecovery<String> recovery = new StartupRecovery<>(scheduler,
                () -> { reads++; return "settings"; }, value -> {
                    installs++; throw new IllegalStateException();
                }, reporter);
        recovery.start(); drain(); recovery.start();
        assertEquals(1, installs); assertEquals(1, reads);
        assertEquals(StartupRecovery.Stage.INSTALL_FAILED, stages.get(stages.size() - 1));
        assertTrue(delays.isEmpty());
    }

    @Test public void brokenDiagnosticsCannotStopRecovery() {
        StartupRecovery<String> recovery = new StartupRecovery<>(scheduler,
                () -> ++reads == 1 ? null : "settings", value -> installs++,
                (stage, attempt, failure) -> { throw new IllegalStateException(); });
        recovery.start(); drain();
        assertEquals(2, reads); assertEquals(1, installs);
    }
}
