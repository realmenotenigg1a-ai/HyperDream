package io.github.hyperdream.module;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SettingsReaderPolicyTest {
    @Test
    public void allowsOwnUidWithoutPackageLookup() {
        assertTrue(SettingsReaderPolicy.allows(10001, 10001, null));
    }

    @Test
    public void allowsLauncherUidIncludingSharedUid() {
        assertTrue(SettingsReaderPolicy.allows(10001, 10002, new String[]{"com.miui.home"}));
        assertTrue(SettingsReaderPolicy.allows(10001, 10002,
                new String[]{"shared.uid.peer", "com.miui.home"}));
    }

    @Test
    public void rejectsUnrelatedAndUnknownCallers() {
        assertFalse(SettingsReaderPolicy.allows(10001, 10002, null));
        assertFalse(SettingsReaderPolicy.allows(10001, 10002, new String[]{}));
        assertFalse(SettingsReaderPolicy.allows(10001, 10002,
                new String[]{null, "com.miui.home.fake", "android"}));
    }
}
