package io.github.hyperdream.module;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Historical reports, not a liveness monitor. Only the authenticated launcher can send IPC. */
final class HookReports {
    static final String METHOD_REPORT = "report_spring_status";
    private static final String PREFERENCES = "hook_reports";
    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "HyperDream-report");
        thread.setDaemon(true);
        return thread;
    });

    enum Status { DISABLED, UNSUPPORTED, REGISTERED, OBSERVED, APPLIED, FAILED }

    private HookReports() {}

    static void send(Context context, Status status, SpringSettings settings) {
        send(context, status, settings, "");
    }

    static void send(Context context, Status status, SpringSettings settings, String diagnostics) {
        final String boundedDiagnostics = diagnostics.substring(0, Math.min(diagnostics.length(), 2048));
        SENDER.execute(() -> {
            try {
                Bundle payload = new Bundle();
                payload.putString("status", status.name());
                payload.putString("build", BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_ID);
                payload.putString("snapshot", settings.snapshot());
                payload.putString("diagnostics", boundedDiagnostics);
                context.getContentResolver().call(Uri.parse("content://" + AnimationSettings.AUTHORITY),
                        METHOD_REPORT, null, payload);
            } catch (RuntimeException ignored) {
                // Diagnostics must never break launcher startup or an animation callback.
            }
        });
    }

    static void record(Context context, Bundle payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Missing hook report");
        }
        Status status = Status.valueOf(payload.getString("status", ""));
        String build = payload.getString("build", "");
        String snapshot = payload.getString("snapshot", "");
        String diagnostics = payload.getString("diagnostics", "");
        if (build.length() > 96 || snapshot.length() > 96 || diagnostics.length() > 2048) {
            throw new IllegalArgumentException("Oversized report");
        }
        preferences(context).edit().putString("status", status.name())
                .putString("build", build).putString("snapshot", snapshot)
                .putString("diagnostics", diagnostics)
                .putLong("time", System.currentTimeMillis()).apply();
    }

    static String describe(Context context) {
        SharedPreferences prefs = preferences(context);
        long timestamp = prefs.getLong("time", 0);
        if (timestamp == 0) {
            return context.getString(R.string.report_none);
        }
        Status status;
        try {
            status = Status.valueOf(prefs.getString("status", ""));
        } catch (IllegalArgumentException invalid) {
            return context.getString(R.string.report_none);
        }
        int label;
        switch (status) {
            case DISABLED: label = R.string.report_disabled; break;
            case UNSUPPORTED: label = R.string.report_unsupported; break;
            case REGISTERED: label = R.string.report_registered; break;
            case OBSERVED: label = R.string.report_observed; break;
            case APPLIED: label = R.string.report_applied; break;
            default: label = R.string.report_failed;
        }
        String build = prefs.getString("build", "");
        String currentBuild = BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_ID;
        boolean matches = currentBuild.equals(build)
                && SettingsStore.readSpring(context).snapshot().equals(prefs.getString("snapshot", ""));
        return context.getString(R.string.report_details, context.getString(label),
                DateFormat.getDateTimeInstance().format(new Date(timestamp)), build,
                context.getString(matches ? R.string.report_historical : R.string.report_stale))
                + "\n\n" + prefs.getString("diagnostics", "");
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
