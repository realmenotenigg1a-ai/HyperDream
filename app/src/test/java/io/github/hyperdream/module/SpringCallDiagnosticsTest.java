package io.github.hyperdream.module;

import static org.junit.Assert.*;
import org.junit.Test;

public final class SpringCallDiagnosticsTest {
    @Test
    public void zeroCallsAreExplicitRatherThanImpliedActivation() {
        String summary = new SpringCallDiagnostics().summary();
        assertTrue(summary.contains("snapshot: 0"));
        assertTrue(summary.contains("Last outcome: NO_CALLS"));
    }

    @Test
    public void filteredTypesAndExceptionsRemainDistinctFromReplacement() {
        SpringCallDiagnostics stats = new SpringCallDiagnostics();
        stats.record("OPEN_FROM_RECENTS", SpringCallDiagnostics.Outcome.FILTERED_TYPE);
        stats.record("OPEN_FROM_HOME", SpringCallDiagnostics.Outcome.ORIGINAL_EXCEPTION);
        stats.record("CLOSE_TO_HOME", SpringCallDiagnostics.Outcome.EMPTY_RESULT);
        String summary = stats.summary();
        assertTrue(summary.contains("snapshot: 3"));
        assertTrue(summary.contains("bundles replaced: 0"));
        assertTrue(summary.contains("Filtered/invalid types: 1"));
        assertTrue(summary.contains("exceptions preserved: 1"));
        assertTrue(summary.contains("empty results preserved: 1"));
        assertTrue(summary.contains("OPEN_FROM_RECENTS"));
        stats.record("OPEN_FROM_HOME", SpringCallDiagnostics.Outcome.REPLACED);
        assertTrue(stats.summary().contains("bundles replaced: 1"));
    }

    @Test
    public void reportingAndTypeMemoryAreBoundedEvenWithManyCalls() {
        SpringCallDiagnostics stats = new SpringCallDiagnostics();
        int reports = 0;
        for (int i = 0; i < 100_000; i++) {
            if (stats.record("TYPE_" + (i % 100), SpringCallDiagnostics.Outcome.FILTERED_TYPE)) {
                reports++;
            }
        }
        assertTrue(reports <= 19);
        assertTrue(stats.summary().contains("TYPE_7"));
        assertFalse(stats.summary().contains("TYPE_8,"));
        assertFalse(stats.summary().contains("TYPE_99"));
    }

    @Test
    public void diagnosticNamesAreBoundedCodeIdentifiersOnly() {
        assertEquals("OPEN_FROM_HOME", SpringCallDiagnostics.safeName("OPEN_FROM_HOME"));
        assertEquals("<unrecognized>", SpringCallDiagnostics.safeName(null));
        assertEquals("<unrecognized>", SpringCallDiagnostics.safeName("injected\ntext"));
        assertEquals("<unrecognized>", SpringCallDiagnostics.safeName("a".repeat(65)));
    }
}
