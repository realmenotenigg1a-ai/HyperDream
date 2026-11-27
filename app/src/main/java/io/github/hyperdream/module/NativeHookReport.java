package io.github.hyperdream.module;

import android.content.Context;
import android.os.SystemClock;
import android.os.UserManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/** Explicit, read-only root collection. No launcher files, complete logcat, or automatic uploads. */
final class NativeHookReport {
    static final String MODULE = "/data/adb/modules/hyperdream_runtime";
    // Fixed command only: no settings or UI text is interpolated into a shell.
    static final String COMMAND = "test \"$(id -u)\" = 0 || exit 77; "
            + "test \"$(am get-current-user)\" = 0 || exit 78; "
            + "echo NATIVE_MODULE_STATUS; "
            + "if test -d " + MODULE + "; then "
            + "head -c 2048 " + MODULE + "/module.prop; echo; "
            + "test ! -f " + MODULE + "/disable || echo DISABLED_PENDING_REBOOT; "
            + "test ! -f " + MODULE + "/remove || echo REMOVAL_PENDING_REBOOT; "
            + "else echo NOT_INSTALLED; fi; "
            + "echo CURRENT_LAUNCHER_PIDS; pids=$(pidof com.miui.home); echo \"$pids\"; "
            + "for p in $pids; do case \"$p\" in ''|*[!0-9]*) continue;; esac; "
            + "name=$(tr '\\000' '\\n' < /proc/$p/cmdline | head -n 1); "
            + "test \"$name\" = com.miui.home || continue; "
            + "echo TARGET_MAPS_PID=$p; "
            + "if test -r /proc/$p/maps; then "
            + "awk '{n=split($NF,a,\"/\"); if(a[n]==\"libapp.so\") app=1; "
            + "if(a[n]==\"libhyper_os_flutter.so\") engine=1} "
            + "END {print \"mapped_libapp=\" (app+0) \" mapped_engine=\" (engine+0)}' /proc/$p/maps; "
            + "else echo MAPS_UNAVAILABLE; fi; done; "
            + "echo NATIVE_LOG_HISTORY; "
            + "/system/bin/logcat -g >/dev/null 2>&1 || exit 82; "
            + "/system/bin/logcat -d -v epoch -s HyperDreamNative:I '*:S' 2>&1"
            + " | tail -n 160 | head -c 32768";
    private NativeHookReport() {}

    static String collectRoot(Context context) {
        if (!RestartGuard.begin()) { return "Collection busy: another root operation is running."; }
        Process process = null;
        File output = null;
        try {
            UserManager users = context.getSystemService(UserManager.class);
            if (users == null || !users.isSystemUser()) { return "Root collection supports owner user only."; }
            output = File.createTempFile("native-report-", ".txt", context.getCacheDir());
            process = new ProcessBuilder("su", "-c", COMMAND).redirectErrorStream(true)
                    .redirectOutput(output).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) { return "Root collection timed out. No native success inferred."; }
            String text;
            try (FileInputStream input = new FileInputStream(output)) {
                byte[] bytes = new byte[49152];
                int count = input.read(bytes);
                text = count <= 0 ? "" : new String(bytes, 0, count, StandardCharsets.UTF_8);
            }
            return "Root collector exit=" + process.exitValue() + "\n"
                    + (text.isEmpty() ? "No output; root may have been denied." : text);
        } catch (IOException unavailable) {
            return "Root collection unavailable: " + unavailable.getClass().getSimpleName();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return "Root collection interrupted.";
        } catch (RuntimeException denied) {
            return "Root collection failed: " + denied.getClass().getSimpleName();
        } finally {
            if (process != null && process.isAlive()) { process.destroyForcibly(); }
            if (output != null) { output.delete(); }
            RestartGuard.release();
        }
    }

    static String assemble(Context context, String runtime, String nativeOutput) {
        AnimationSettings settings = SettingsStore.read(context);
        return "HYPERDREAM HOOK REPORT\nGenerated: " + new Date()
                + "\nUptime ms: " + SystemClock.elapsedRealtime()
                + "\n\n" + DeviceDiagnostics.describe(context)
                + "\n\nSAVED UI SETTINGS (not proof of application)"
                + "\nDuration enabled=" + settings.enabled + " scale=" + settings.durationScale
                + " curve=" + settings.curve + " scaleDelays=" + settings.scaleDelays
                + "\nSpring: " + SettingsStore.readSpring(context).snapshot()
                + "\n\nNATIVE ANIMATION STATUS: NOT IMPLEMENTED / NOT APPLIED"
                + "\nThis build observes native library loading only. Duration/curve/spring controls"
                + " do not change native launcher animations."
                + "\nNative stages: RUNTIME_REGISTRATION(result=0) -> LAUNCHER_SPECIALIZED"
                + " -> OBSERVER_INSTALLED -> IMAGE_SEEN. None means animation applied."
                + "\nLogs are historical, may be absent/rotated, and may contain older builds/PIDs."
                + " Compare build=" + BuildConfig.BUILD_ID + " and the launcher PID."
                + " Absence does not prove a loader failure. Module files and mapped libraries do not prove the observer loaded."
                + "\n\nNATIVE COLLECTOR\n" + nativeOutput
                + "\n\nAPK RUNTIME INSPECTION\n" + runtime
                + "\n\nJAVA HOOK HISTORY (separate from native observer)\n" + HookReports.describe(context)
                + "\n\nNo automatic upload. Review this report before sharing.";
    }
}
