# HyperDream 1.2-probe: native Runtime diagnostic test

## What this build does (and does not do)

This is a **native loading / library observer test**, not a working native animation customization release. Existing Java controls and local previews are retained. Native duration, curve and spring replacement are **not implemented**. There is no validated launcher animation ABI/profile yet. We do not guess offsets or Dart calling conventions.

The separate root-manager ZIP registers a Zygisk Next API v4 module for `/system_ext/bin/hyos_spawner`. It registers HYOS Runtime API v1 and admits only **both package and main process `com.miui.home`**. Only after launcher specialization does it install a pass-through observer on the standard `android_dlopen_ext` entry. It never hooks the shared spawner's loader. The observer calls the original loader exactly once with unchanged arguments, then reports only `libapp.so` / `libhyper_os_flutter.so` identities. It also inventories libraries already mapped at specialization. No global clock scaling, launcher code patching, persistent launcher file modification, threads, JNI or guessed addresses are used.

Observing a library load is a real native observer hook, **not an animation hook**. Runtime library identity helps select a future exact-build resolver, but does not identify the animation functions by itself. A later step still needs matching binary/source analysis and a validated resolver before duration can be changed. A loader path that bypasses `android_dlopen_ext` may not be observed; missing images are inconclusive.

## Prerequisites

- Owner user (user 0), arm64 device, native HyperOS Runtime (`hyos_spawner`).
- Zygisk Next exposing **module API v4 and HYOS Runtime API v1**. Regular Magisk Zygisk alone is not this API. A version string is not proof of API support.
- Root manager supporting standard module ZIPs and Zygisk Next module discovery.
- Existing HyperDream Java hooks still use LSPosed; the probe ZIP is a separate native loading path and does not require adding System Framework or System UI to scope.
- Working recovery/root-manager disable mechanism. This remains unverified native code on your device and may crash the launcher. Back up first; do not test without a way to disable root modules if Home is unavailable.

## Install and test

1. Download/extract the GitHub Actions distribution archive. It contains **two installables**: the debug-signed `HyperDream-1.2-probe-…apk` and `HyperDream-NativeProbe-1.2-probe-…zip`.
2. Update the APK normally. If Android rejects a different debug signing key, back up settings before considering uninstall/reinstall; do not silently wipe the app.
3. Install the **inner NativeProbe ZIP** in your root manager. Do not install the outer Actions archive, and do not flash either ZIP in recovery. Review installer warnings.
4. Reboot so Zygisk Next loads the module. No new LSPosed scope is needed for the native probe.
5. Open and close a few apps, then open **HyperDream → Device → Hook report → Collect with root**. Allow the root request. Collection runs off the UI thread with a 30-second timeout and does not restart anything.
6. Review and **Copy** or **Share report**. Send the complete text, including absent/failed stages. Without root still gives the Java history, version information and APK library inspection, but cannot retrieve native logs.
7. Do not expect duration/curve sliders to change the native launcher in this build.

## Reading the report

Every native log line includes the payload's Git revision, PID, a stage and `animation_applied=0`. Compare the build with the APK identity and PID with `CURRENT_LAUNCHER_PIDS`. Logs are bounded historical evidence, not an authenticated liveness or success attestation. Collection is limited to the `HyperDreamNative` tag (160 lines, 32 KiB), bounded module status, launcher PIDs, and current target-library mapping presence flags (not addresses or full map paths). It does **not** collect full logcat, accounts, serials, screenshots or application content; sharing is explicit and never automatic.

- `RUNTIME_REGISTRATION result=0`: API accepted the callback in the spawner; not launcher execution.
- `LAUNCHER_SPECIALIZED`: exact launcher package/process callback reached.
- `OBSERVER_INSTALLED`: pass-through loader observer installed, **not animation applied**.
- `OBSERVER_FAILED` / `OBSERVER_UNAVAILABLE`: observer could not install. Report the result.
- `IMAGE_SEEN`: target ELF appeared, with bounded GNU build ID if available. No base addresses or full library paths are logged. `unavailable` is not a guessed identity.
- `mapped_libapp` / `mapped_engine`: current mappings in an exact-name validated launcher process. Presence is independent evidence only, not proof the observer ran.
- `NOT_INSTALLED`: module directory absent. Installed files / disabled markers are not proof of what is loaded in the current process; changes require reboot.
- No lines: could be root denial, unsupported API, disabled module, log rotation, tag filtering or failed injection. Do not infer a working hook or definitive failure.

## Recovery / uninstall

Disable **HyperDream Native Runtime Probe** in the root manager and reboot. If Home is unavailable, use another reachable app/ADB or the root manager's documented safe mode/recovery mechanism. If authorized recovery shell access is available, create `/data/adb/modules/hyperdream_runtime/disable` and reboot. Removing the APK alone does **not** remove the separately installed root module. Do not disable unrelated framework modules as a first step.

## Provenance / validation

The probe is an independent implementation of the public ABI described by:
- https://github.com/Dr-TSNG/ZygiskNext/blob/main/docs/hyos_runtime.md
- https://github.com/Dr-TSNG/ZygiskNext/blob/main/zygisk_next_api.h

No HyperLight implementation is included. No source copied from HyperLight, KeiHome or Zygisk Next implementations. The minimal ABI declarations preserve the documented v4 function-pointer order, including unused slots.

Host tests cover exact process/library guards and malformed/truncated ELF notes with ASan/UBSan. CI compiles arm64 using pinned NDK/CMake, checks the `zn_module` export, assembles and signs the APK, packages the root module and runs Android tests/lint. These checks do **not** establish device compatibility, Zygisk delivery, SELinux behavior, launcher stability or animation effects.
