package io.github.hyperdream.module;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class NativeHookReportTest {
    @Test public void collectorIsReadOnlyBoundedAndTagFiltered() {
        String command = NativeHookReport.COMMAND;
        assertTrue(command.contains("id -u"));
        assertTrue(command.contains("am get-current-user"));
        assertTrue(command.contains("-s HyperDreamNative:I '*:S'"));
        assertTrue(command.contains("tail -n 160 | head -c 32768"));
        assertTrue(command.contains("pidof com.miui.home"));
        assertFalse(command.contains("kill "));
        assertFalse(command.contains("force-stop"));
        assertFalse(command.contains("logcat -c"));
        assertFalse(command.contains("chmod"));
        assertFalse(command.contains("/data/data/"));
    }

    @Test public void missingNativeEvidenceNeverBecomesApplied() {
        String report = NativeHookReport.assemble(RuntimeEnvironment.getApplication(),
                "runtime unavailable", "Root denied");
        assertTrue(report.contains("NOT IMPLEMENTED / NOT APPLIED"));
        assertTrue(report.contains("Root denied"));
        assertTrue(report.contains("historical"));
        assertTrue(report.contains("Absence does not prove"));
        assertTrue(report.contains("build=" + BuildConfig.BUILD_ID));
        assertTrue(report.contains("No automatic upload"));
    }

    @Test public void nativeObserverDoesNotImplyAnimationHookSuccess() {
        String report = NativeHookReport.assemble(RuntimeEnvironment.getApplication(), "inspection",
                "stage=OBSERVER_INSTALLED animation_applied=0");
        assertTrue(report.contains("None means animation applied"));
        assertTrue(report.contains("NOT IMPLEMENTED / NOT APPLIED"));
        assertTrue(report.contains("JAVA HOOK HISTORY (separate from native observer)"));
    }
}
