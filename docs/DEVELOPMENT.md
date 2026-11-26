# Developing HyperDream

## Baseline

- Android application ID / Java package: `io.github.hyperdream.module`.
- Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9.
- Compile/target SDK 35, minimum SDK 31.
- Classic Xposed API 82, provided by LSPosed at runtime (`compileOnly`).
- No Kotlin, AndroidX, native code, or preferences bridge yet.

The application ID is an initial project choice, not a claim of ownership of a separate GitHub organization.

## Layout

```text
app/src/main/
  AndroidManifest.xml             App components and classic module metadata
  assets/xposed_init              Fully qualified module entry class
  java/io/github/hyperdream/module/
    HyperDreamEntry.java          Diagnostic-only package-load callback
    TargetPackages.java           Package/process allowlist
    MainActivity.java             Informational screen, not an activation detector
  res/                            UI, icon, strings, and recommended scope
app/src/test/                     JVM allowlist tests
.github/workflows/android.yml     Build, unit tests, lint, APK artifact
```

## Local verification

Import the repository into Android Studio with JDK 17 and SDK 35 installed, then run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

`local.properties` may contain `sdk.dir=/absolute/path/to/Android/sdk`. It is ignored by Git. Do not commit local SDK paths, signing keys, or credentials.

The Xposed API is resolved from `https://api.xposed.info/`, restricted to the `de.robv.android.xposed` group. Other dependencies come from Google Maven and Maven Central. The Gradle wrapper also needs access to its distribution host. A network-restricted environment may not be able to build even when Java and the SDK are installed.

The initial scaffold was created in an environment without a JDK or Android SDK; an Android build and on-device verification must be performed separately. The presence of a CI workflow does not mean that CI has already run successfully.

## Device smoke test

Use a test device with a working LSPosed installation and a known recovery route.

1. Install the debug APK. Verify the HyperDream screen opens and describes its diagnostic-only status.
2. Enable the module and scope it only to `com.miui.home` in LSPosed.
3. Reboot and verify the `[HyperDream] Loaded in com.miui.home main process` message in LSPosed logs.
4. Confirm the launcher still opens and behaves normally. No animation behavior should change.
5. Disable the module and reboot. Confirm normal launcher operation and no new HyperDream load message for that boot. Historical log entries may remain.

Record the device, Android version, full HyperOS build, launcher version, and LSPosed version. Do not label a build supported solely because its entry point loads.

## Adding animation hooks later

1. Inspect the actual target launcher build and identify relevant classes, methods, and signatures. Do not guess private HyperOS APIs.
2. Document the tested firmware and launcher versions before adding a hook.
3. Keep the package/process allowlist. Do not broaden scope to `android`, System UI, or every installed app as a workaround.
4. Skip unsupported classes/signatures safely and log an actionable diagnostic without exposing private user data. Do not conceal failures behind a false success status.
5. Keep all experimental behavior off by default and provide a restoration path before exposing controls.
6. Design and test any settings bridge separately; ordinary app preferences are not automatically readable from another process.
7. Add tests, device verification steps, and explicit compatibility notes with each hook.

The current entry point intentionally calls no hook-installation APIs. Do not add fake sliders or claim working animation controls until the underlying behavior is implemented and verified.
