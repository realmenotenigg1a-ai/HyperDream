package io.github.hyperdream.module;

import android.content.Context;
import android.content.SharedPreferences;

/** App-private storage. The launcher reads a sanitized snapshot via SettingsProvider. */
final class SettingsStore {
    private SettingsStore() {}

    static AnimationSettings read(Context context) {
        SharedPreferences prefs = preferences(context);
        try {
            return new AnimationSettings(
                    prefs.getBoolean(AnimationSettings.KEY_ENABLED, false),
                    prefs.getFloat(AnimationSettings.KEY_DURATION_SCALE, 1f),
                    prefs.getString(AnimationSettings.KEY_CURVE, AnimationSettings.CURVE_SYSTEM),
                    prefs.getBoolean(AnimationSettings.KEY_SCALE_DELAYS, false));
        } catch (ClassCastException malformedPreferences) {
            return new AnimationSettings(false, 1f);
        }
    }

    static void write(Context context, AnimationSettings settings) {
        preferences(context).edit()
                .putBoolean(AnimationSettings.KEY_ENABLED, settings.enabled)
                .putFloat(AnimationSettings.KEY_DURATION_SCALE,
                        settings.durationScale)
                .putString(AnimationSettings.KEY_CURVE, settings.curve)
                .putBoolean(AnimationSettings.KEY_SCALE_DELAYS, settings.scaleDelays)
                .apply();
    }

    /** Call off the UI thread before explicitly restarting the settings reader. */
    static boolean flush(Context context) {
        // A synchronous commit waits for outstanding apply() writes as documented by Android.
        return preferences(context).edit().commit();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(AnimationSettings.PREFERENCES, Context.MODE_PRIVATE);
    }
}
