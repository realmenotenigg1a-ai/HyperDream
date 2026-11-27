# Developing HyperDream

## Baseline

- Android application ID / Java package: `io.github.hyperdream.module`.
- Java source/bytecode 17; JDK 17 or 21 for Gradle (CI uses 21 for SDK tools).
- Android Gradle Plugin 8.7.3, Gradle 8.9, compile/target SDK 35, Build-Tools 35.0.0, minimum SDK 31.
- Classic Xposed API 82, provided by LSPosed at runtime (`compileOnly`).
- No Kotlin, native code, or world-readable preference files. Runtime UI uses platform Android views; Robolectric tests have AndroidX test transitives. Root is requested only for the explicit restart shortcut.

The application ID is an initial project choice, not a claim of ownership of a separate GitHub organization.

## Architecture

```text
HyperDream app → SettingsStore (private SharedPreferences)
                           ↑
Launcher → SettingsProvider.call(read_settings) → validated snapshot
    │       Checks Binder UID: own app or com.miui.home; no remote writes
    └── Application.attach callback → LauncherAnimationHook
                                      └── ValueAnimator.setDuration / setInterpolator / setStartDelay
```

`HyperDreamEntry` first checks both package and process names. The startup callback reads settings once, after `Application.attach`. A process-local guard prevents duplicate setup. Disabled/original settings install no animation hooks. A failed read also installs no animation hooks. Curve-only overrides are supported at 1×. Registration failure rolls back hooks registered in that attempt. The provider is intentionally exported for cross-UID reads and authenticated launcher diagnostic submissions, but checks the caller against Android's UID-to-package mapping; it never trusts a package name supplied in a request. Android shared-UID peers have the same security identity and therefore the same read access. No personal data is exposed, only animation settings. The separate report method authenticates the launcher UID before accepting bounded status/build/snapshot fields; it never changes configuration.

The provider is not direct-boot aware. Unavailable/locked settings must leave behavior unchanged. No launcher hooks are installed from the UI process, System UI, or System Framework.

`AnimationSettings` accepts only the four UI presets. Invalid floats fall back to 1×. Negative durations are passed unchanged to Android for its normal validation, zero stays zero, positive values never round down to zero, and multiplication saturates instead of overflowing. `AnimatorSet.setDuration` is deliberately not hooked, to avoid scaling both a set and its child animators.

The hook uses a public Android API rather than invented HyperOS class names. It is intentionally broad within the launcher, but incomplete: it does not cover private spring engines, default durations that never pass through the setter, remote transitions, or other processes. Repeated application calls that read back and reassign an already-scaled duration can compound the multiplier. Device testing is essential.

## Build and automated checks

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Configure the SDK via Android Studio, `ANDROID_HOME`, or an ignored `local.properties` file with `sdk.dir=/absolute/path/to/Android/sdk`. Never commit SDK paths or signing credentials.

The JVM suite covers target-process selection, duration/delay math, invalid settings, overflow, settings-reader authorization, and restart command/result policy. Robolectric additionally inflates the Android screen and checks control persistence/reset and curve endpoints/monotonicity. These tests do not execute Xposed hooks, root commands, or real cross-process Android IPC. Provider authorization, startup timing, hook dispatch, and process restart behavior need device verification.

The Xposed API resolves from `https://api.xposed.info/`, restricted to its Maven group. Other dependencies use Google Maven and Maven Central; the Gradle distribution uses its own host. Network restrictions can prevent a build. CI uses the Android SDK tools already provided by `ubuntu-latest`, rather than downloading a second command-line-tools installation.

This workspace has no JDK or Android SDK. Android compilation, JVM tests, and lint must run in CI or an Android development environment; static checks alone do not prove the APK builds or works.

## Device test checklist

Use a recoverable test device. Record model, Android version, full HyperOS build, launcher version, LSPosed version, and module version.

1. Install the debug APK. Verify controls default to off / 1× and remain saved after closing/reopening the app and after reboot.
2. Enable only `com.miui.home` in LSPosed and reboot. Verify the load log and the off/1× log. Launcher behavior should be unchanged.
3. Enable the control with 0.75×, reboot, and look for the hook-installed log. Compare affected launcher animations with the baseline. A successful log does not prove an app transition uses ValueAnimator.
4. Repeat with 0.5× and 1.5×. Check folders, home navigation, widgets, recents, rotation, and app launch/return for visual regressions. Record unchanged effects too.
5. Select 1× with original easing and restart: no animation overrides should be installed. Test a curve-only override at 1× separately. Repeat with the enable switch off.
6. Restore defaults, reboot, and confirm off/1×, unchanged launcher behavior, and the off/1× startup log.
7. Verify a non-launcher app cannot read settings via provider `call`, and no caller can write through insert/update/delete. An ADB shell read should also be rejected unless that installation gives it the same UID as the launcher.
8. Verify missing/unavailable provider access leaves launcher startup working with no duration hook.
9. Disable the module in LSPosed, reboot, and confirm no new HyperDream load message for that boot (historical logs may remain).

For recovery, disable HyperDream through LSPosed and reboot. Prepare the recovery route documented by your installed root/LSPosed solution before testing; there is no universal safe-mode command guaranteed for every installation. This project never kills or force-stops the launcher automatically.

## Future HyperOS-specific work

Inspect an actual target launcher APK and document the class/method signatures and tested versions before adding private hooks. Keep unsupported signatures as safe no-ops with actionable diagnostics. Do not broaden scope to System Framework or System UI to make a launcher-specific feature work. Any later settings change must preserve off-by-default behavior and a tested restoration route.

## Settings UI and motion

The Android settings app is part of the LSPosed APK, not a separate requirement to convert the module into an app. `MainActivity` edits real duration, easing, and delay settings and renders a roadmap for features that are not implemented.

- The root and system bars use solid black `app_background`; the unused decorative backdrop was removed in v0.6.1. `glass_panel.xml` still provides translucent card fills and highlights. There is no expensive continuously running blur, optical shader, or live wallpaper sampling.
- `MotionPreviewView` draws an abstract desktop and animates a local icon/card pair. It uses the selected duration, curve, and delay settings but intentionally ignores the enable switch, as labeled in the interface. It never calls the launcher or validates hook activation.
- `ValueAnimator.areAnimatorsEnabled()` skips entry motion and substitutes a still preview when global animation duration scale is zero. Standard Android animation scaling otherwise applies to in-app playback.
- Entry animations are staggered once on a fresh Activity creation. `onStop` cancels them, restores visible card state, removes pending entry callbacks, and stops preview playback; view detachment also stops playback. There is no perpetual animation loop.
- Window insets include system bars and display cutouts. Text uses scalable units, cards wrap content, controls have at least 48dp targets, and preview artwork has a content description. The interface still needs physical-device verification at large font scales and with TalkBack.

UI smoke tests: rotate while previewing; background/reopen during entry and playback; replay repeatedly; change presets during playback; restore defaults; set system animation duration to zero; try gesture and three-button navigation; test landscape, a narrow display, 200% font scaling, and a screen reader. No card should remain transparent, no animation should continue in the background, and local previewing must not modify stored enable state or install launcher hooks.

See [Feature scope](FEATURES.md) for the nonfunctional roadmap and prerequisites for future Home Up-inspired controls.

## User-confirmed launcher restart

`LauncherRestarter` is called only after a confirmation dialog. A worker thread checks the owner profile, installed launcher, and default Home; flushes pending private preference writes; then executes the fixed `su -c` command in `LauncherRestartPolicy`. No caller-controlled data is concatenated into the command. It checks root identity, force-stops only `com.miui.home` for the current user, and starts its Home activity. It never clears app data, kills System UI, or reboots the device.

A process-local guard prevents overlapping requests. A 30-second wait bounds root authorization/execution. The command output is kept in a temporary private file, read with a bounded buffer, never uploaded, and deleted after the attempt. A zero exit code, completion marker, and absence of known Android error output are required before reporting command completion. This is **not** proof the launcher loaded the module or any animation effect worked. Denial, missing root, profile/default-Home mismatch, timeout, and settings-flush failures are surfaced to the user, with manual reboot as fallback. No restart runs on preference changes, activation, or boot.

Additional device tests: cancel the confirmation (no root prompt); deny root; grant root; withhold authorization until timeout; try a non-Xiaomi default Home; try a secondary/work profile; confirm no app data loss; restart after every new setting and after restore defaults; inspect LSPosed logs for the selected curve/delay values. A root shell timeout can leave command outcome uncertain, so verify Home manually before retrying.

`MotionCurves` returns null for original/invalid values (preserve the launcher interpolator), or bounded monotonic easing presets. Only explicit setter calls after initialization are intercepted. The same limitations as duration apply: private spring engines and system-managed transitions are outside these hooks. An easing override may alter effects beyond app launch/return, so it remains experimental.

## Java spring adapter (v0.5)

See [Spring compatibility](SPRING-COMPATIBILITY.md) for the inspected API, upstream research reference, strict shape checks, native-launcher limitation, historical-report semantics, and missing device verification.

`SpringSettings` is separate from `AnimationSettings`. `LauncherAnimationHook` reads both snapshots after `Application.attach` and installs the spring adapter independently of the generic animator switch. `SpringParamAdapter` performs an all-or-nothing typed copy and leaves original objects untouched. The callback only replaces OPEN_FROM_HOME/CLOSE_TO_HOME results, preserves original exceptions, and stops modifying parameters after unexpected data. Synchronization orders REGISTERED before APPLIED/FAILED reports even when target calls occur on other launcher threads. Reports are sent off-thread. v0.6 adds bounded diagnostic sampling and two one-off timed snapshots; there is no continuous polling loop.

Tests use structural fixture classes, not decompiled HyperOS code or an actual hook runtime. Additional Robolectric tests cover spring slider persistence/reset, tab visibility, visible build identity, settings reads, rejection of app-forged launcher reports, and distinction between registered/applied/stale historical reports.

`package_debug.sh` verifies the generated APK with Android `apksigner`, extracts package/version metadata, and writes a version/build-named APK with checksums. `BuildConfig.BUILD_ID` comes from `GITHUB_SHA` (or `local` outside CI). PR workflow SHA values may be merge-test revisions rather than the branch head; the packaged filename, build info, and UI agree on that build revision. Debug keys are not a stable release identity and updates signed by a different key require uninstall/reinstall.

## Diagnostic capture (v0.6)

`SpringCallDiagnostics` keeps counters and at most eight bounded ASCII enum identifiers. Count-driven reports occur at powers of two up to 1024 calls and on first observation of one of the first eight names. First replacement/failure always publishes; startup and two fixed snapshots at 15s/60s cover zero calls. A generous upper bound is 24 snapshot submissions per launcher process. The main-thread callback performs no file/network IPC itself; reports are queued to the existing worker. No global tracing, stack capture, coordinates, app names, or screen data are collected. Counters in the UI describe the last reported snapshot, not a live total. The hook still preserves exceptions, filters types, and fails closed.

`NativeLibraryProbe` checks ZIP directory entries for three known Flutter-related library names in four ABIs across at most 32 installed APK paths. It reads no library payload, extracts nothing, and executes no native code. Evidence is capped at twelve entries. Unreadable paths are marked partial. The result is only library-presence evidence; a matching basename is not ELF validation, signature verification, active-runtime detection, or a reason to install native hooks. The scan runs on the activity worker and its output is included only in user-initiated diagnostics copying.

Synthetic ZIP/counter tests cover split-library discovery, partial scans, input bounds, filtered/exception/empty/replaced distinctions, and bounded sampling. Robolectric tests cover OBSERVED report rendering and oversized-payload rejection. These are not real launcher/Xposed tests.

## Plain background and report shortcut (v0.6.1)

The root layout references a single solid black color shared with the window and system-bar theme attributes. No backdrop drawable or decorative background layer remains. Cards and the explicitly local motion preview retain their existing styling.

Spring → Copy diagnostic report and Device → Copy build details share one click handler; both copy current build/runtime details and the latest stored hook report. Neither initiates a hook, changes settings, or uploads data. A toast confirms the copy so a user can paste the text into chat without navigating to another page. Robolectric tests check the solid background and shortcut clipboard contents.


## Floating glass navigation (v0.7)

`LiquidGlassNavigationView` is a sibling overlay above `studio_scroll`, not a child of the scroll content. The root keeps existing system-bar/cutout padding; the scroll has 124dp bottom clearance for the 90dp bar + 16dp bottom margin. The three equal regions are at least 48dp tall and exposed as virtual radio buttons. Selection is saved by index; invalid indices clamp safely. Keyboard arrows select destinations. A user may drag only the lens; all positions, including overshoot-animation frames, pass through `GlassNavigationGeometry.clamp`. The valid centre region is the bar capsule eroded by lens radius plus 2dp, so axis-only clamping cannot leak through rounded corners.

Capture draws only the sibling ScrollView into a capped-width (320px) in-memory bitmap. It never captures its own overlay, the launcher, other windows or protected surfaces. Ancestor sources are rejected to prevent recursive drawing. Source-dirty pre-draws (not navigation-only redraws), scroll/layout, page/entrance animation completion and Activity resume request coalesced captures (minimum 33ms spacing); there is no perpetual idle render timer. Stop/detach cancels pending frames and lens animation; detach removes source listeners. Images stay in memory and are not logged, written, or uploaded. Both paths use three separable box-blur passes; the bitmap fallback uses subtle 1.04× lens magnification. Android 13+ loads `NavigationGlassShader` separately and binds the preblurred live bitmap into the AGSL resource; software/unsupported rendering uses the bitmap path. Cards and background are not themselves live glass. Black backdrops correctly remain dark.

CI additionally compiles the AGSL subset in Skia (`.github/scripts/check_glass_shader.py`) and checks transparent capsule corners and differing output for red/blue captured input. This validates shader syntax/sampling, not Android GPU equivalence. Robolectric covers overlay placement/clearance, dragging beyond all edges, destination selection, recreation, ancestor-source rejection and accessible actions. Pure JVM tests stress rounded-end containment and actual blur pixel math.

Manual checks still required: drag toward each corner/edge and cancel a gesture; tap all destinations; rotate/recreate while on Device; gesture vs three-button navigation; system animator scale 0; TalkBack exploration/selected state; large fonts; scroll bright cards behind the bar and compare to black content; Android 12 fallback vs Android 13+ hardware rendering; profile scroll frame time and repeated background/resume. No physical device visual/performance results are claimed.


## Bounded Java startup recovery (v0.7.1)

`LauncherAnimationHook` independently registers attach and base-onCreate callbacks and checks `AndroidAppHelper.currentApplication()` for late entry. The first valid Context wins an atomic guard, then a main-Handler `StartupRecovery<Bundle>` chain reads settings. `StartupRecovery` is Android-independent and tests one installation, successful recovery, six-read exhaustion/56s total scheduled delay, immediate authorization-denial exit, no retry after partial install, and diagnostics isolation. Read IPC is still synchronous on the main thread, as before; these retries do not introduce an unbounded worker or process poller. Callback/entry delivery and cross-process Binder behavior require actual device verification.

`StartupReports` uses the existing serialized reporting executor and a separate UID-authenticated provider operation/store. Status strings, build IDs, attempts and exception class names are bounded; exception messages or stacks are not copied. `HookReports.describe` and both clipboard actions include startup plus spring evidence. Missing startup evidence is not proof of no injection; initialization is not proof of interception; interception is not proof of visible animation use. At most three report-delivery warnings per process go to Android logs, never from the UI as a fake launcher report.


## Glass readability and interaction polish (v0.8)

The reported screenshot shows replicated sharp glyphs under navigation. The old API 33+ shader bound the **unblurred** capture and combined nine widely spaced taps, producing copies instead of a continuous blur. The already-computed blurred bitmap was only used by the fallback. Both paths now consume `BackdropBlur.frost` (three box passes, using reusable work buffers). AGSL samples that surface with linear filtering, lower displacement/channel separation and an 82% dark tint. The fallback likewise darkens the surface. A capture exception clears partial pixels before publication. Captures are capped at 320px wide, listeners/coalescing are unchanged, and icon/label drawing remains outside the optical shader. No screen background texture or hooking changes were added.

`StudioMotion` installs enabled/pressed/default `StateListAnimator` states on Button subclasses (including switches/radios) and SeekBars. It does not consume MotionEvents or replace native click handlers. Selected/keyboard-focused option backgrounds use a stateful ripple drawable. Motion is checked on resume, on acknowledgment and on nav selection. Stop resets scales and cancels transient text/details animations. Dialog actions get an isolated controller disposed on dismiss. The nav icon pulse is cancelled alongside lens settling; lens geometry is unchanged.

Technical compatibility details are collapsed by default but explicitly available on Home, with their state saved in the instance bundle. Device owns the restart/configuration block; Home and Animator no longer start below it. Every page has an appropriate heading/caption; tab scroll offsets are independently saved/restored. Labels still distinguish local saves, install checkpoints and real interception evidence.

Manual acceptance: compare the bar over bright, small text before/after, including API 32 fallback and API 33+ hardware rendering; scroll/fling and drag the lens while pages animate; inspect bottom controls in gesture/three-button modes; switch tabs rapidly during entrance; press/cancel/release each control without duplicated saves; navigate with keyboard/TalkBack; try large fonts and animator scale zero; background during a press or details expansion; rotate while scrolled/expanded. CI pixel-math and Skia tests do not establish the actual Xiaomi GPU appearance or frame timing.
