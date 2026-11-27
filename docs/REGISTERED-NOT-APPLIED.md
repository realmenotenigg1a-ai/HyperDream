# Investigating launcher 801030308 without a binary upload

## What was retrieved

The user supplied a MobileOS Updates download-started URL for `RELEASE-8.01.03.308-261102-11171006-R`. The [version page](https://mobileosupdates.com/apps/com.miui.home/801030308) identifies package `com.miui.home`, version code `801030308`, and size 57.09 MB. Its download-started page requires the request to originate from the app page/browser session. The sandbox could not retrieve raw page/binary data from that host. No APK, DEX, or ELF from this URL has been downloaded, authenticated, decompiled, or executed. The query token is intentionally not stored in the repository.

Do not follow a mirror's generic advice to uninstall a system launcher. No launcher replacement, device system modification, or public redistribution of Xiaomi binaries is required for the diagnostic build.

## Related public evidence, not device verification

- [KeiHome's incident report at c02cc210](https://github.com/hosizoraru/KeiHome/blob/c02cc21061eadf0e13c194795a324ba190891f6b/docs/launcher-boot-incident-20261125.md) describes launcher `801030308` on a HyperOS 4 device using the native HyperOS Runtime path and a separate native module entry point. It also reports framework-specific startup crashes and later follow-up observations. These are that project's observations, not tests performed by HyperDream. They do not justify prescribing a framework upgrade/downgrade for this user.
- [HyperGlass adaptation notes at c0c565b5](https://github.com/finall1008/HyperGlass/blob/c0c565b5349fa8ce1b3b3cd5bc82da3cf1ddd233/docs/launcher-adaptation.md) describe a `libapp.so` reference for `801030308` and native/ARM64 adaptation and visual testing. No source from that repository is bundled or copied.
- [Zygisk Next HyperOS Runtime documentation](https://github.com/LSPosed/ZygiskNext/blob/main/docs/hyos_runtime.md) is relevant background for a future native implementation; HyperDream's current APK does not implement that loading chain.

A version string is not an ELF fingerprint or proof this user's APK has identical contents. These sources explain why Java-only hooks are an inadequate assumption, not a complete native implementation plan.

## Diagnostic build

1. Verify the header says `v0.7.1-recovery` and the expected build ID from the artifact filename.
2. Enable HyperDream in LSPosed with only `com.miui.home` scoped. Enable the separate Java spring override in HyperDream.
3. Restart the launcher or reboot, open apps from Home and close back to Home several times, and leave the launcher process alive for at least 60 seconds.
4. Open HyperDream and refresh the Spring report. Read Device → Launcher runtime evidence after the local inspection completes.
5. Use **Copy build details** and paste the text. It includes version/build, OS/launcher version, ZIP-library metadata, and the last stored hook snapshot. No serial, account data, screenshots, coordinates, or app contents are included.

If a new CI debug certificate prevents updating, disable the old module, uninstall the old testing app (losing its settings), install the new APK, re-enable scope, and restart/reboot. A stale report from another module build is not evidence for this test.

## Interpreting evidence

| Evidence | Meaning / next step |
| --- | --- |
| No report | Check scope, framework delivery, startup, and settings-provider access; not an enum-filter diagnosis |
| Registered + zero calls after snapshots | Java method resolved but no invocation was observed. It may be dead/legacy code, another path, an optimization issue, or a native transition; do not broaden enum filters blindly |
| Observed + filtered types | Real callbacks occurred; inspect the reported enum names and actual semantics before considering another allowlisted animation type |
| Observed + null/empty results | The provider was called without a usable bundle. Preserve the result and permit later valid calls |
| Original exceptions | The launcher itself threw; preserve those exceptions and investigate rather than replacing the result |
| Applied + nonzero replacement count | A Java callback supplied replacement parameters, not proof of visible transition use |
| Native app + engine library entries | Flutter/native components are packaged. Java hooks may target legacy code, but presence alone does not establish which runtime owns every animation |
| Partial ZIP inspection | Missing evidence is inconclusive; do not claim native libraries are absent |

Sampling is bounded and the report is historical. Refresh does not force the launcher to call a method. Two one-off snapshots at 15s and 60s make a zero-call outcome explicit; there is no continuous poller.

## What is not fixed

The diagnostic build adds no arbitrary native offsets, in-memory code patches, native loaders, automatic deoptimization, or expanded System UI/framework scope. A real native port requires the correct framework/runtime ABI, exact binary analysis or semantic resolution, lifecycle/ownership checks, isolated tests, recovery controls, and physical-device validation. None of those can be replaced by a successful Java registration log.


## New evidence and safest recovery attempt (v0.7.1)

The user supplied an in-app report from v0.7.0 / `6a9be62c`: Xiaomi 24129PN74C, Android 17/API 37, OS4.0.4.0.XOCCNXM, launcher 801030308. ZIP metadata reports `lib/arm64-v8a/libapp.so`, 31,124,752 bytes, CRC32 `225e1966`; no complete engine/app library pair in accessible APKs. The engine can live outside those APKs; missing pair evidence is not proof of Java-only execution. The user confirms reboot and enabled launcher-only scope. Screenshots show LSPosed 2.2.1 (7912), API 102, and the launcher marked **HyperOS Runtime**. Other modules log inside System UI/system; those do not establish HyperDream delivery into the launcher. No HyperDream entry logs or spring reports were found.

The previous Java startup path had a single `Application.attach` hook and one settings read. v0.7.1 adds independent base-onCreate and late-current-Application fallbacks, one guarded main-thread settings-read/recovery chain, bounded retries for temporary read failures, and separately authenticated startup checkpoints. Security denial is terminal. Once installation starts it is never retried; an installation failure may leave the independent spring adapter installed and is reported as `INSTALL_FAILED`, not success. Generic hook rollback retains its existing behavior.

New copyable checkpoint meanings:

- `CONTEXT_READY` / `READING_SETTINGS`: Java startup reached a Context; no spring-install or animation-success claim.
- `WAITING_FOR_SETTINGS`: temporary failure; another bounded read is scheduled.
- `SETTINGS_UNAVAILABLE`: six reads exhausted; no installation attempted.
- `SETTINGS_DENIED`: provider authorization denied; no retry or installation.
- `INSTALLING` / `INITIALIZED`: settings returned; installation routine began/returned. Read the independent spring outcome for DISABLED/UNSUPPORTED/REGISTERED/OBSERVED/APPLIED.
- `INSTALL_FAILED`: installation threw; no reinstallation loop.
- No startup checkpoint: still inconclusive between no Java entry, callback issues, and report transport failure. Recovery cannot manufacture Java entry into a native-only process.

This build attempts fixes for missed callbacks and transient IPC availability only. It is not a native integration implementation and is not known to fix this physical device. Do not recommend broader scope, framework replacement or disabling protection based only on these screenshots. No further binary-upload request is required for this diagnostic attempt.
