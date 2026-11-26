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
                    prefs.getFloat(AnimationSettings.KEY_DURATION_SCALE, 1f));
        } catch (ClassCastException malformedPreferences) {
            return new AnimationSettings(false, 1f);
        }
    }

    static void write(Context context, boolean enabled, float scale) {
        preferences(context).edit()
                .putBoolean(AnimationSettings.KEY_ENABLED, enabled)
                .putFloat(AnimationSettings.KEY_DURATION_SCALE,
                        AnimationSettings.sanitizeScale(scale))
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(AnimationSettings.PREFERENCES, Context.MODE_PRIVATE);
    }
}
