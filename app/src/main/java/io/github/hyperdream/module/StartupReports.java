package io.github.hyperdream.module;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import java.text.DateFormat;
import java.util.Date;

/** Separate historical startup evidence. Never labels startup as a working animation hook. */
final class StartupReports {
    static final String METHOD_REPORT = "report_startup_status";
    private StartupReports() {}

    static void send(Context context, StartupRecovery.Stage stage, int attempt, String failure) {
        Bundle payload = new Bundle();
        payload.putString("stage", stage.name());
        payload.putInt("attempt", attempt);
        payload.putString("failure", failure);
        payload.putString("build", BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_ID);
        HookReports.enqueue(() -> {
            try {
                Bundle result = context.getContentResolver().call(
                        Uri.parse("content://" + AnimationSettings.AUTHORITY), METHOD_REPORT, null, payload);
                if (result == null) { HookReports.logDeliveryFailure("startup", "NullAcknowledgement"); }
            } catch (RuntimeException failureToSend) {
                HookReports.logDeliveryFailure("startup", failureToSend.getClass().getSimpleName());
            }
        });
    }

    static void record(Context context, Bundle payload) {
        if (payload == null) { throw new IllegalArgumentException("Missing startup report"); }
        String stage = StartupRecovery.Stage.valueOf(payload.getString("stage", "")).name();
        int attempt = payload.getInt("attempt", -1);
        String build = payload.getString("build", "");
        String failure = payload.getString("failure", "");
        if (attempt < 0 || attempt > 6 || build.length() > 96 || !failure.matches("[A-Za-z0-9_$]{0,96}")) {
            throw new IllegalArgumentException("Invalid startup report");
        }
        preferences(context).edit().putString("stage", stage).putInt("attempt", attempt)
                .putString("build", build).putString("failure", failure)
                .putLong("time", System.currentTimeMillis()).apply();
    }

    static String describe(Context context) {
        SharedPreferences prefs = preferences(context);
        long time = prefs.getLong("time", 0);
        if (time == 0) { return context.getString(R.string.startup_none); }
        String build = prefs.getString("build", "");
        return context.getString(R.string.startup_details, prefs.getString("stage", ""),
                prefs.getInt("attempt", 0), prefs.getString("failure", ""), build,
                DateFormat.getDateTimeInstance().format(new Date(time)),
                context.getString((BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_ID).equals(build)
                        ? R.string.report_historical : R.string.startup_stale));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("startup_reports", Context.MODE_PRIVATE);
    }
}
