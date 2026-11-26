<div align="center">

<img src="docs/assets/hyperdream-icon.png" alt="HyperDream app icon" width="192" height="192" />

# HyperDream

**Your animations. Your flow. Your HyperOS.**

An animation-customization module targeting **Xiaomi HyperOS 4**, inspired by the home-screen customization experience of **Samsung Home Up**.

**Status: Early development · Experimental LSPosed motion controls · Device testing needed**

</div>

---

## What is HyperDream?

HyperDream aims to give you more control over how your phone feels in motion. The goal is to let you personalize app transitions and launcher animations on HyperOS 4, rather than relying only on the system's default animation style.

Home Up is the inspiration for the level of customization—not a dependency. HyperDream is an independent project, not a port of Samsung Home Up.

## Planned features

The following are goals, **not currently working features**. Scope may change as HyperOS compatibility and technical limitations are investigated.

- **App opening and closing animations** — customize transitions when launching apps and returning to the home screen.
- **Animation timing** — adjust transition duration and speed where supported.
- **Motion tuning** — explore configurable easing and spring-like motion.
- **Home-screen transitions** — personalize supported launcher transitions, including folder animations.
- **Recent-apps transitions** — explore customization of app-switching and recents animations.
- **Animation presets** — switch between curated motion styles and a system-default option.
- **Restore defaults** — provide a way to revert changes made by HyperDream.

## Target platform and compatibility

| Item | Status |
| --- | --- |
| Target system | Xiaomi HyperOS 4 |
| Supported devices | Not yet established |
| Supported system / launcher builds | Not yet established |
| Module framework | LSPosed, using the classic Xposed API 82 |
| Runtime requirements | A rooted test device with a compatible, working LSPosed installation |
| Installation package | Build a debug APK from source; no published release yet |

HyperOS 4 is the intended target, not a claim of verified support. Compatibility must be tested against specific devices, firmware, and launcher versions. Support for earlier HyperOS versions is not currently promised.

## Development roadmap

- [x] Define the HyperDream name and visual identity.
- [x] Document the project vision and initial feature goals.
- [ ] Investigate the target system and launcher's animation behavior.
- [x] Select LSPosed and add the basic Android module scaffold.
- [x] Implement an opt-in launcher ValueAnimator duration prototype (device verification pending).
- [x] Add duration presets, an enable switch, and a restore-defaults action.
- [x] Implement disabled-by-default settings and document basic recovery guidance.
- [ ] Test on explicitly documented device and firmware combinations.
- [ ] Publish a first test release with installation instructions and known issues.

## v0.6 diagnostic: investigate registered-but-unused hooks

This build does **not** claim to fix native HyperOS 4 transitions. It adds evidence needed when the Java hook registers but no effect is observed:

- Bounded counts for actual provider callbacks, filtered enum types, original exceptions, empty results, replacements, and adapter failures.
- Declared and observed animation enum names; no coordinates, screenshots, or app contents.
- Sampled reports plus one snapshot at 15 seconds and one at 60 seconds, including an explicit zero-call count if the method is never used. Refresh reads the last stored snapshot; it does not invoke a launcher method or prove liveness.
- Null/empty bundles are preserved without disabling later valid calls.
- Read-only base/split APK ZIP metadata inspection for Flutter/native library indicators, off the UI thread and without root, extraction, execution, or upload.

The user-supplied download page identifies version `801030308`, but the APK could not be retrieved for inspection. Public development reports describe a native HyperOS Runtime path for that exact version; these are third-party reports, not verification of the installed APK. See [the investigation and diagnostic protocol](docs/REGISTERED-NOT-APPLIED.md).

To help without uploading an APK: enable spring overrides, restart/reboot, exercise app opening/closing, wait at least 60 seconds, refresh the Spring report, then use **Device → Copy build details** and paste the resulting text. Device details include the runtime-library evidence and last hook snapshot. If the copy says inspection is still pending, wait and copy again.

## v0.5: Java spring adapter and identifiable builds

The **Spring** tab now has independent damping and response controls connected to a guarded Java `SpringParamsProvider` hook for app open-from-home / close-to-home transitions. This is different from the generic Animator tab's speed/easing controls.

**This is not verified HyperOS 4 support.** The adapter requires an exact Java launcher API; native-only launchers cannot use it. HyperSpring's own documentation warns about this distinction. See [spring compatibility and research references](docs/SPRING-COMPATIBILITY.md) before enabling it.

The launcher reports **disabled / unsupported / registered / parameters replaced / failed**. Reports include the loaded module build and are explicitly historical, not a live “active” badge. “Registered” is not shown as “working.” A mismatch with current settings/build is labeled stale.

The polished interface is split into **Spring / Animator / Device** pages, with an always-visible version/build identity and shared restart action. CI packages a uniquely named APK with SHA-256 checksums, package metadata, build information, and an `apksigner` verification report so builds can be distinguished.

## Current implementation

The experimental controls hook **Android `ValueAnimator` duration, interpolator, and optional start-delay setters inside the Xiaomi launcher's main process**. It does not change global animation scales or other apps' processes.

- **Off by default** — enable the control explicitly in the HyperDream app.
- **Duration presets** — 0.5× (fast), 0.75× (quick), 1× (original), and 1.5× (relaxed). These multiply duration, not speed.
- **Easing presets** — original, smooth, liquid glide (emphasized ease-out), or linear. A curve-only override works even at 1× duration.
- **Optional delay scaling** — scales explicit `setStartDelay` calls by the duration multiplier; off by default.
- **Restore defaults** — disables the controls, resets the multiplier to 1×, restores original easing, and stops scaling delays.
- **Restart to apply** — settings are read once at launcher startup. Use the root-confirmed launcher restart shortcut or reboot, including after disabling/resetting.
- **Launcher restart** — a fixed, user-confirmed root command force-stops and reopens only `com.miui.home`. Owner profile and Xiaomi launcher as default Home are required. No data is cleared; no automatic restart occurs.
- **Build details** — displays device/Android incremental build and launcher version, with user-initiated copying. It does not certify HyperOS support.
- **Read-only settings bridge** — app-private configuration is exposed only to the module's own UID and the Xiaomi launcher's UID. Settings cannot be remotely written. Only the launcher UID may submit limited historical hook reports.
- **Restricted scope** — only `com.miui.home`, with an additional main-process check in code.
- **Failure fallback** — if settings cannot be read, the animation hook is not installed.

### Glass-inspired motion studio

The settings app now uses a blue/teal gradient backdrop, translucent rounded cards, edge highlights, and ripple/soft-press controls. This is a **liquid-glass-inspired visual treatment**, not live background blur or optical refraction.

A replayable local motion illustration shows the selected duration, easing, and optional delay settings without requiring LSPosed. It is labeled as an illustration and works even when the module control is off. The selected preset is also wired to launcher interpolator setter calls, but the preview is not proof any particular HyperOS transition uses them. Original easing in the preview uses demonstration curves, not the device's curves. Entry transitions and preview playback respect the system animation-off setting, and playback stops when the app leaves the foreground.

The in-app **Beyond motion** section lists planned home-screen, folder, recents, gesture, advanced-animation, and layout/sharing work. Those are not working controls. See the [feature scope and implementation gates](docs/FEATURES.md); this is not a claim that every Home Up feature has been implemented or is possible on HyperOS.

### Generic Animator limitations

This is **not yet a Home Up equivalent**. It has not been verified on a HyperOS 4 device. It only affects explicit `ValueAnimator.setDuration`, `setInterpolator`, and optional `setStartDelay` calls after initialization; it does not override values that never pass through those setters. ObjectAnimator calls delegating to that API can be affected too.

The control may change multiple launcher effects, not just app open/close animations. Private spring engines, render-thread animations, system-managed remote transitions, and animations in other processes may not be affected at all. Custom curve editing, native spring effects, launcher icon sizing/grids, per-app controls, recents styles, and a live launcher preview are not implemented.

The app's Android 12+ minimum SDK is a development baseline, **not** a claim of HyperOS compatibility. This project uses the classic Xposed API rather than the modern libxposed API. The settings screen distinguishes saved configuration from historical spring-hook reports. Neither is a live certification of module activation or visible animation quality.

## Build

Requirements: **JDK 17 or 21**, Android SDK Platform **35**, Android SDK Build-Tools **35.0.0**, and internet access to the configured Gradle/Maven repositories. Android Studio can install the SDK components.

```sh
# Configure the SDK through Android Studio, ANDROID_HOME, or local.properties.
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat`. The Gradle 8.9 wrapper is included. CI uses JDK 21 for current Android SDK tools. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs the same checks and uploads a `HyperDream-debug-<commit>` artifact after a successful build. The archive contains `HyperDream-<version>-<build>-debug.apk`, `SHA256SUMS.txt`, `SIGNATURE.txt`, `PACKAGE.txt`, and `BUILD-INFO.txt`. It is a development build, not a verified compatible release. The app header shows the same version/build ID. Never identify an APK just by its icon or filename. CI debug certificates can differ between runs: if Android refuses an update because the signing key differs, disable the old module, uninstall the old test app (losing its local settings), install the new one, re-enable scope, and restart/reboot. These are not production-signed updates.

See [Development notes](docs/DEVELOPMENT.md) for the project layout, verification steps, and boundaries for future hooks.

## Install and test the experimental control

There is **no published release yet**, and this implementation has not been verified on a HyperOS 4 device.

1. Build the debug APK and install it on a recoverable test device with a compatible LSPosed installation.
2. Enable **HyperDream** in LSPosed.
3. Select only **Xiaomi System Launcher (`com.miui.home`)** in the module scope. Do not add System Framework or System UI.
4. Reboot the device and open the launcher.
5. With settings still off, check LSPosed logs for:

   ```text
   [HyperDream] Loaded in com.miui.home main process.
   ```

6. Open HyperDream, choose a duration preset such as **0.75×**, and enable experimental motion controls.
7. Use **Restart Xiaomi launcher** and confirm/grant root, or reboot. Look for `[HyperDream] Launcher ValueAnimator hooks registered: duration=0.75` in LSPosed logs, then test launcher behavior. That log only confirms hook registration, not that any particular transition uses it.
8. To revert generic changes, tap **Restore animator defaults**. To revert spring changes, use **Disable and reset springs**. Restart the launcher or reboot after either action.

Opening the HyperDream app does not verify module activation. The diagnostic message verifies entry-point loading only; it does not establish animation support. Devices with a different launcher package are not targeted by this implementation.

To remove the module, disable HyperDream in LSPosed, reboot, and uninstall the app. If the launcher is inaccessible, use your LSPosed/root solution's documented module-disabling recovery procedure; the steps depend on that installation. Prepare and test that recovery path before experimenting.

**Install the APK normally. Do not flash this repository, its source archive, or the APK as a Magisk/KernelSU ZIP.**

## Safety

Modifying launcher or system animation behavior can cause crashes, visual glitches, or boot problems. Before testing any future release, back up important data and make sure you know how to disable the module and recover your device.

Device-specific recovery instructions and known limitations must be documented before a test release is offered.

## Contributing

Ideas, design feedback, and development contributions are welcome. Open an issue to discuss a proposed feature or technical approach before making substantial changes.

For future compatibility reports, include:

- Device model.
- Full HyperOS build number and Android version.
- System launcher version.
- Module version and framework, once available.
- Steps to reproduce the issue and relevant logs, with personal information removed.

Please distinguish feature requests from behavior tested on a real device.

## Disclaimer

HyperDream is an independent community project. It is not affiliated with, endorsed by, or supported by Xiaomi or Samsung. HyperOS, One UI, and Home Up belong to their respective owners.
