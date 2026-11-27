package io.github.hyperdream.module;

/** Android authorizes IPC by UID; a shared UID is one security identity. */
final class SettingsReaderPolicy {
    private SettingsReaderPolicy() {}

    static boolean allows(int ownUid, int callingUid, String[] callerPackages) {
        if (ownUid == callingUid) {
            return true;
        }
        return isLauncher(callerPackages);
    }

    static boolean isLauncher(String[] callerPackages) {
        if (callerPackages != null) {
            for (String name : callerPackages) {
                if (TargetPackages.MIUI_HOME.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
