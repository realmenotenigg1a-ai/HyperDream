package io.github.hyperdream.module;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Call only after explicit confirmation, on a worker. No force-stop, data clearing or reboot. */
final class SystemUiRestarter {
    enum Result { REQUEST_COMPLETED, ROOT_UNAVAILABLE, COMMAND_FAILED, TIMEOUT,
        PACKAGE_UNAVAILABLE, PROFILE_UNSUPPORTED, BUSY }
    private SystemUiRestarter() {}

    static Result restart(Context context) {
        if (!RestartGuard.RUNNING.compareAndSet(false, true)) { return Result.BUSY; }
        java.lang.Process process = null;
        File output = null;
        try {
            UserManager users = context.getSystemService(UserManager.class);
            if (users == null || !users.isSystemUser()) { return Result.PROFILE_UNSUPPORTED; }
            context.getPackageManager().getPackageInfo(SystemUiRestartPolicy.PACKAGE, 0);
            output = File.createTempFile("systemui-restart-", ".log", context.getCacheDir());
            process = new ProcessBuilder("su", "-c", SystemUiRestartPolicy.COMMAND)
                    .redirectErrorStream(true).redirectOutput(output).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) { return Result.TIMEOUT; }
            String text;
            try (FileInputStream input = new FileInputStream(output)) {
                byte[] buffer = new byte[16384];
                int length = input.read(buffer);
                text = length <= 0 ? "" : new String(buffer, 0, length, StandardCharsets.UTF_8);
            }
            return SystemUiRestartPolicy.requestCompleted(process.exitValue(), text)
                    ? Result.REQUEST_COMPLETED : Result.COMMAND_FAILED;
        } catch (PackageManager.NameNotFoundException unavailable) {
            return Result.PACKAGE_UNAVAILABLE;
        } catch (IOException unavailable) {
            return Result.ROOT_UNAVAILABLE;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return Result.COMMAND_FAILED;
        } catch (RuntimeException denied) {
            return Result.COMMAND_FAILED;
        } finally {
            if (process != null && process.isAlive()) { process.destroyForcibly(); }
            if (output != null) { output.delete(); }
            RestartGuard.RUNNING.set(false);
        }
    }
}
