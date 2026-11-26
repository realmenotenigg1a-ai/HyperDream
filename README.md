<div align="center">

<img src="docs/assets/hyperdream-icon.png" alt="HyperDream app icon" width="192" height="192" />

# HyperDream

**Your animations. Your flow. Your HyperOS.**

An animation-customization module targeting **Xiaomi HyperOS 4**, inspired by the home-screen customization experience of **Samsung Home Up**.

**Status: Early development · LSPosed scaffold · No animation hooks yet**

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
- [ ] Build a minimal animation-customization prototype.
- [ ] Add a settings interface and animation controls.
- [ ] Implement default restoration and document recovery steps.
- [ ] Test on explicitly documented device and firmware combinations.
- [ ] Publish a first test release with installation instructions and known issues.

## Current implementation

- A Java Android app with the HyperDream icon and an informational launcher screen.
- Classic Xposed module metadata and an `assets/xposed_init` entry point for LSPosed.
- Recommended scope limited to **Xiaomi System Launcher (`com.miui.home`)**.
- An additional in-code package/process allowlist: only the launcher's main process logs a diagnostic message.
- No animation hooks, system setting changes, network permissions, or activation-status claims.
- Unit tests for the allowlist and a GitHub Actions workflow for tests, lint, and a debug APK build.

The app's Android 12+ minimum SDK is a scaffold baseline, **not** a claim of HyperOS compatibility. This project uses the classic Xposed API rather than the modern libxposed API.

## Build

Requirements: **JDK 17**, Android SDK Platform **35**, Android SDK Build-Tools **35.0.0**, and internet access to the configured Gradle/Maven repositories. Android Studio can install the SDK components.

```sh
# Configure the SDK through Android Studio, ANDROID_HOME, or local.properties.
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat`. The Gradle 8.9 wrapper is included. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs the same checks and uploads a `HyperDream-debug` artifact after a successful build. An artifact is a development build, not a verified compatible release.

See [Development notes](docs/DEVELOPMENT.md) for the project layout, verification steps, and boundaries for future hooks.

## Install and verify the scaffold

There is **no published release yet**, and this scaffold has not been verified on a HyperOS 4 device.

1. Build the debug APK and install it on a recoverable test device with a compatible LSPosed installation.
2. Enable **HyperDream** in LSPosed.
3. Select only **Xiaomi System Launcher (`com.miui.home`)** in the module scope. Do not add System Framework or System UI.
4. Reboot the device and open the launcher.
5. Check LSPosed logs for:

   ```text
   [HyperDream] Loaded in com.miui.home main process; diagnostic scaffold only, no animation hooks installed.
   ```

Opening the HyperDream app does not verify module activation. The diagnostic message verifies entry-point loading only; it does not establish animation support. Devices with a different launcher package are not targeted by this scaffold.

To remove the scaffold, disable HyperDream in LSPosed, reboot, and uninstall the app. If the launcher is inaccessible, use your LSPosed/root solution's documented module-disabling recovery procedure; the steps depend on that installation. Prepare and test that recovery path before experimenting.

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
