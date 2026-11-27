# Java spring adapter: what is and is not supported

## Research reference

The API investigation used the public [HyperSpring project](https://github.com/olilio3/HyperSpring), particularly [`Springs.java` at efac9463](https://github.com/olilio3/HyperSpring/blob/efac94632c2ad59e978d4a83920a191a3fb9478e/app/src/main/java/com/icehome/up/Springs.java). That project is Apache-2.0 licensed. HyperDream's adapter is independently implemented with structural validation, immutable replacement, separate settings, and historical reporting; no HyperSpring source or assets are bundled.

Importantly, the [upstream README at that revision](https://github.com/olilio3/HyperSpring/blob/efac94632c2ad59e978d4a83920a191a3fb9478e/README.md) says HyperOS 4 is untested and warns that its launcher features require a Java launcher, not a native-only stock implementation. This is an upstream compatibility warning, not independent verification of every HyperOS 4 build.

**Renaming a module or targeting a newer Android SDK does not make a native launcher hookable through Java LSPosed hooks.** Do not install an untrusted launcher APK as a workaround.

## Targeted signature

Only in `com.miui.home` main process, after `Application.attach`, with spring overrides explicitly enabled:

```text
com.miui.home.recents.util.SpringParamsProvider$Companion
  generateSpringBundlesByAnimType(
    com.miui.home.recents.util.RectFSpringAnim$AnimType,
    android.graphics.RectF,
    android.graphics.RectF
  ) -> SpringParamsProvider$SpringParam[]
```

The adapter checks that the animation type is an enum, the return type is exactly the expected array, and each parameter has the expected constructor and instance fields:

```text
SpringParam(String key, float damping, float response, float impulse)
```

Only `OPEN_FROM_HOME` and `CLOSE_TO_HOME` results are replaced. Other animation types, exceptions thrown by the launcher, missing classes/signatures, and malformed values are left alone.

This is a **signature guard**, not a tested-build allowlist. Matching names and types alone cannot prove compatible semantics or visual quality. No specific HyperOS 4 launcher version has been tested.

## Controls

- Separate spring enable switch; independent of generic ValueAnimator settings.
- Response multiplier: 0.5–1.5, applied to the original response (lower is faster).
- Damping ratio: 0.5–1.2, replacing damping on every returned parameter for the two allowed transition types. Lower values permit more bounce.
- Original keys and impulses are preserved. An entirely new typed array of new parameter objects is constructed; launcher-owned objects are not mutated.
- Disabled by default. Missing/corrupt settings fall back to disabled. Reset disables spring overrides and resets both numeric controls to 1; restart/reboot removes the previous process's override.

Null/empty bundles are preserved as normal absent results without disabling later calls. Unknown parameter shape, nonempty arrays larger than 64, nonfinite inputs, or out-of-range response values reject the whole replacement. No partial result is returned. The callback then stops applying overrides for the remainder of that launcher process and reports failure. This is not a general crash-loop detector or a guarantee against visual regressions.

## Reports, not fake activation badges

The launcher reports through the settings provider. Reports are accepted only from a UID whose package mapping contains `com.miui.home` (shared-UID peers share that security identity). App-private preferences store the last state, receiving wall-clock timestamp, module version/build ID, and spring-setting snapshot. No diagnostic is uploaded externally.

| State | What happened |
| --- | --- |
| No report | No authenticated report was received; scope/startup/provider access may be wrong |
| Disabled | Launcher read spring settings as disabled |
| Unsupported | Expected Java API/shape was absent or hook installation failed |
| Registered | The exact method was hooked; observed callback count is zero at this snapshot |
| Observed | Callbacks occurred but returned original exceptions, filtered types, invalid arguments, or null/empty bundles without a replacement |
| Applied | The callback intercepted a supported type and supplied a complete replacement array |
| Failed | Unexpected runtime data caused the adapter to stop applying overrides in that process |

**Applied does not prove the user saw the intended visual effect.** These are historical reports, not a live process monitor. Build/settings mismatches are labeled stale. Even a matching report can outlive the process or a reboot. Restart after changes, exercise app opening/closing, and explicitly refresh; old reports must not be treated as proof of current activation.

## Verification still needed

CI tests validate the adapter against small structural fixtures, range checks, all-or-nothing copying, settings persistence/reset, report authorization, and UI navigation. They do not execute real Xposed interception or a real launcher.

For actual HyperOS 4 support, supply the full OS build, launcher version/APK, LSPosed version, and a recoverable test device. Verify the API exists, whether transition paths call it, what units/fields mean, the visible effect, reset, and safe recovery. Native-only implementations require a different technical approach and are not supported by this adapter.

## Registered-but-unused investigation

v0.6 adds bounded invocation counters, declared/observed enum names, staged installation failure reasons, two one-off delayed snapshots, and an independent read-only native-library metadata probe. See [the diagnostic protocol](REGISTERED-NOT-APPLIED.md). A Flutter library pair is an indicator, not proof every animation is native. The module still does not provide a native HyperOS Runtime entry point or Dart/ARM64 hooks.
