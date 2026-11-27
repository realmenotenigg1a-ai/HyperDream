package io.github.hyperdream.module;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Explicit, root-approved restart only. Call on a worker thread, never on app startup. */
final class LauncherRestarter {
    enum Result {
        REQUEST_COMPLETED, ROOT_UNAVAILABLE, COMMAND_FAILED, TIMEOUT,
        LAUNCHER_UNAVAILABLE, NOT_DEFAULT_HOME, PROFILE_UNSUPPORTED, BUSY, SAVE_FAILED
    }


    private LauncherRestarter() {}

    static Result restart(Context context) {
        if (!RestartGuard.RUNNING.compareAndSet(false, true)) {
            return Result.BUSY;
        }
        java.lang.Process process = null;
        File outputFile = null;
        try {
            UserManager users = context.getSystemService(UserManager.class);
            if (users == null || !users.isSystemUser()) {
                return Result.PROFILE_UNSUPPORTED;
            }
            PackageManager packages = context.getPackageManager();
            packages.getPackageInfo(TargetPackages.MIUI_HOME, 0);
            ResolveInfo home = packages.resolveActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            if (home == null || home.activityInfo == null
                    || !TargetPackages.MIUI_HOME.equals(home.activityInfo.packageName)) {
                return Result.NOT_DEFAULT_HOME;
            }
            if (!SettingsStore.flush(context)) {
                return Result.SAVE_FAILED;
            }
            outputFile = File.createTempFile("launcher-restart-", ".log", context.getCacheDir());
            process = new ProcessBuilder("su", "-c", LauncherRestartPolicy.COMMAND)
                    .redirectErrorStream(true).redirectOutput(outputFile).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                return Result.TIMEOUT;
            }
            String output;
            try (FileInputStream input = new FileInputStream(outputFile)) {
                byte[] buffer = new byte[16384];
                int length = input.read(buffer);
                output = length <= 0 ? "" : new String(buffer, 0, length, StandardCharsets.UTF_8);
            }
            return LauncherRestartPolicy.requestCompleted(process.exitValue(), output)
                    ? Result.REQUEST_COMPLETED : Result.COMMAND_FAILED;
        } catch (PackageManager.NameNotFoundException missingLauncher) {
            return Result.LAUNCHER_UNAVAILABLE;
        } catch (IOException missingRootOrIoFailure) {
            return Result.ROOT_UNAVAILABLE;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.COMMAND_FAILED;
        } catch (RuntimeException deniedOrUnavailable) {
            return Result.COMMAND_FAILED;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (outputFile != null) {
                // Contains only command output, never uploaded or shown as a public log.
                outputFile.delete();
            }
            RestartGuard.RUNNING.set(false);
        }
    }
}
