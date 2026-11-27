package io.github.hyperdream.module;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TargetPackagesTest {
    @Test
    public void acceptsOnlyLauncherMainProcess() {
        assertTrue(TargetPackages.isLauncherMainProcess("com.miui.home", "com.miui.home"));
    }

    @Test
    public void rejectsOtherPackagesEvenWithLauncherProcessName() {
        assertFalse(TargetPackages.isLauncherMainProcess("android", "com.miui.home"));
        assertFalse(TargetPackages.isLauncherMainProcess("com.android.systemui", "com.android.systemui"));
        assertFalse(TargetPackages.isLauncherMainProcess("io.github.hyperdream.module", "io.github.hyperdream.module"));
    }

    @Test
    public void rejectsLauncherSecondaryProcesses() {
        assertFalse(TargetPackages.isLauncherMainProcess("com.miui.home", "com.miui.home:remote"));
    }

    @Test
    public void rejectsMissingNames() {
        assertFalse(TargetPackages.isLauncherMainProcess(null, null));
        assertFalse(TargetPackages.isLauncherMainProcess("com.miui.home", null));
        assertFalse(TargetPackages.isLauncherMainProcess(null, "com.miui.home"));
        assertFalse(TargetPackages.isLauncherMainProcess("", ""));
    }
}
