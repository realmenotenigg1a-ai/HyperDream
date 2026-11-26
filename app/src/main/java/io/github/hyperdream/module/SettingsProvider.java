package io.github.hyperdream.module;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Read-only settings bridge plus UID-authenticated launcher diagnostic reports. */
public final class SettingsProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (HookReports.METHOD_REPORT.equals(method)) {
            enforceLauncherReporter();
            HookReports.record(providerContext(), extras);
            return Bundle.EMPTY;
        }
        enforceReader();
        if (!AnimationSettings.METHOD_READ.equals(method)) {
            throw new UnsupportedOperationException("Unknown settings operation");
        }
        AnimationSettings settings = SettingsStore.read(providerContext());
        Bundle result = new Bundle();
        result.putBoolean(AnimationSettings.KEY_ENABLED, settings.enabled);
        result.putFloat(AnimationSettings.KEY_DURATION_SCALE, settings.durationScale);
        result.putString(AnimationSettings.KEY_CURVE, settings.curve);
        result.putBoolean(AnimationSettings.KEY_SCALE_DELAYS, settings.scaleDelays);
        SpringSettings spring = SettingsStore.readSpring(providerContext());
        result.putBoolean(SpringSettings.KEY_ENABLED, spring.enabled);
        result.putFloat(SpringSettings.KEY_RESPONSE, spring.responseMultiplier);
        result.putFloat(SpringSettings.KEY_DAMPING, spring.damping);
        return result;
    }

    private android.content.Context providerContext() {
        android.content.Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Provider not initialized");
        }
        return context;
    }

    private void enforceLauncherReporter() {
        String[] packages = providerContext().getPackageManager()
                .getPackagesForUid(Binder.getCallingUid());
        if (SettingsReaderPolicy.isLauncher(packages)) {
            return;
        }
        throw new SecurityException("Only the launcher UID may report hook status");
    }

    private void enforceReader() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) {
            return;
        }
        String[] packages = providerContext().getPackageManager().getPackagesForUid(uid);
        if (SettingsReaderPolicy.allows(Process.myUid(), uid, packages)) {
            return;
        }
        throw new SecurityException("Only HyperDream and the scoped launcher may read settings");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("Use read_settings");
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }
}
