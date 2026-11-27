package io.github.hyperdream.module;

import java.util.LinkedHashSet;
import java.util.Set;

/** Process-local bounded counters. Contains code enum names, never coordinates or app content. */
final class SpringCallDiagnostics {
    enum Outcome { FILTERED_TYPE, INVALID_ARGUMENT, ORIGINAL_EXCEPTION, EMPTY_RESULT,
        REPLACED, ADAPTER_FAILED, ADAPTER_STOPPED }

    private final Set<String> observedTypes = new LinkedHashSet<>();
    private long calls;
    private long replaced;
    private long filtered;
    private long originalExceptions;
    private long emptyResults;
    private long failures;
    private Outcome lastOutcome;

    boolean record(String type, Outcome outcome) {
        calls = increment(calls);
        String safeType = safeName(type);
        boolean newType = observedTypes.size() < 8 && observedTypes.add(safeType);
        lastOutcome = outcome;
        switch (outcome) {
            case REPLACED: replaced = increment(replaced); break;
            case FILTERED_TYPE:
            case INVALID_ARGUMENT: filtered = increment(filtered); break;
            case ORIGINAL_EXCEPTION: originalExceptions = increment(originalExceptions); break;
            case EMPTY_RESULT: emptyResults = increment(emptyResults); break;
            case ADAPTER_FAILED: failures = increment(failures); break;
            default: break;
        }
        // At most 11 count thresholds + 8 new types over the whole process lifetime.
        return newType || (calls <= 1024 && (calls & (calls - 1)) == 0);
    }

    static String safeName(String name) {
        if (name == null || name.isEmpty() || name.length() > 64) { return "<unrecognized>"; }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '$')) {
                return "<unrecognized>";
            }
        }
        return name;
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

    String summary() {
        return "Provider calls observed at this snapshot: " + calls
                + "\nParameter bundles replaced: " + replaced
                + "\nFiltered/invalid types: " + filtered
                + "\nOriginal exceptions preserved: " + originalExceptions
                + "\nNull/empty results preserved: " + emptyResults
                + "\nAdapter failures: " + failures
                + "\nObserved enum types (up to 8): " + observedTypes
                + "\nLast outcome: " + (lastOutcome == null ? "NO_CALLS" : lastOutcome.name());
    }
}
