# Developing HyperDream

## Baseline

- Android application ID / Java package: `io.github.hyperdream.module`.
- Java source/bytecode 17; JDK 17 or 21 for Gradle (CI uses 21 for SDK tools).
- Android Gradle Plugin 8.7.3, Gradle 8.9, compile/target SDK 35, Build-Tools 35.0.0, minimum SDK 31.
- Classic Xposed API 82, provided by LSPosed at runtime (`compileOnly`).
- No Kotlin, AndroidX, native code, root shell commands, or world-readable preference files.

The application ID is an initial project choice, not a claim of ownership of a separate GitHub organization.

## Architecture

```text
HyperDream app → SettingsStore (private SharedPreferences)
                           ↑
Launcher → SettingsProvider.call(read_settings) → validated snapshot
    │       Checks Binder UID: own app or com.miui.home; no remote writes
    └── Application.attach callback → LauncherAnimationHook
                                      └── ValueAnimator.setDuration(long)
```

`HyperDreamEntry` first checks both package and process names. The startup callback reads settings once, after `Application.attach`. A process-local guard prevents duplicate setup. Disabled/default settings install no duration hook. A failed read also installs no duration hook. The provider is intentionally exported for cross-UID reads, but checks the caller against Android's UID-to-package mapping; it never trusts a package name supplied in a request. Android shared-UID peers have the same security identity and therefore the same read access. No personal data is exposed, only an enabled flag and multiplier.

The provider is not direct-boot aware. Unavailable/locked settings must leave behavior unchanged. No new hooks are installed from the UI process, System UI, or System Framework.

`AnimationSettings` accepts only the four UI presets. Invalid floats fall back to 1×. Negative durations are passed unchanged to Android for its normal validation, zero stays zero, positive values never round down to zero, and multiplication saturates instead of overflowing. `AnimatorSet.setDuration` is deliberately not hooked, to avoid scaling both a set and its child animators.

The hook uses a public Android API rather than invented HyperOS class names. It is intentionally broad within the launcher, but incomplete: it does not cover private spring engines, default durations that never pass through the setter, remote transitions, or other processes. Repeated application calls that read back and reassign an already-scaled duration can compound the multiplier. Device testing is essential.

## Build and automated checks

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Configure the SDK via Android Studio, `ANDROID_HOME`, or an ignored `local.properties` file with `sdk.dir=/absolute/path/to/Android/sdk`. Never commit SDK paths or signing credentials.

The JVM suite covers target-process selection, duration math, invalid settings, overflow, and settings-reader authorization. These tests do not execute Xposed hooks or Android IPC. Provider authorization, startup timing, hook dispatch, and process restart behavior need device verification.

The Xposed API resolves from `https://api.xposed.info/`, restricted to its Maven group. Other dependencies use Google Maven and Maven Central; the Gradle distribution uses its own host. Network restrictions can prevent a build. CI uses the Android SDK tools already provided by `ubuntu-latest`, rather than downloading a second command-line-tools installation.

This workspace has no JDK or Android SDK. Android compilation, JVM tests, and lint must run in CI or an Android development environment; static checks alone do not prove the APK builds or works.

## Device test checklist

Use a recoverable test device. Record model, Android version, full HyperOS build, launcher version, LSPosed version, and module version.

1. Install the debug APK. Verify controls default to off / 1× and remain saved after closing/reopening the app and after reboot.
2. Enable only `com.miui.home` in LSPosed and reboot. Verify the load log and the off/1× log. Launcher behavior should be unchanged.
3. Enable the control with 0.75×, reboot, and look for the hook-installed log. Compare affected launcher animations with the baseline. A successful log does not prove an app transition uses ValueAnimator.
4. Repeat with 0.5× and 1.5×. Check folders, home navigation, widgets, recents, rotation, and app launch/return for visual regressions. Record unchanged effects too.
5. Select 1× and reboot: no duration hook should be installed. Repeat with the enable switch off.
6. Restore defaults, reboot, and confirm off/1×, unchanged launcher behavior, and the off/1× startup log.
7. Verify a non-launcher app cannot read settings via provider `call`, and no caller can write through insert/update/delete. An ADB shell read should also be rejected unless that installation gives it the same UID as the launcher.
8. Verify missing/unavailable provider access leaves launcher startup working with no duration hook.
9. Disable the module in LSPosed, reboot, and confirm no new HyperDream load message for that boot (historical logs may remain).

For recovery, disable HyperDream through LSPosed and reboot. Prepare the recovery route documented by your installed root/LSPosed solution before testing; there is no universal safe-mode command guaranteed for every installation. This project never kills or force-stops the launcher automatically.

## Future HyperOS-specific work

Inspect an actual target launcher APK and document the class/method signatures and tested versions before adding private hooks. Keep unsupported signatures as safe no-ops with actionable diagnostics. Do not broaden scope to System Framework or System UI to make a launcher-specific feature work. Any later settings change must preserve off-by-default behavior and a tested restoration route.
