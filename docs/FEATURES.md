# Feature scope and compatibility

HyperDream is inspired by the customization depth of Home Up, not a port or a claim of feature parity. Home Up varies across releases and devices; the categories below are our proposed work areas, **not an exhaustive or verified inventory of Samsung features**. No Samsung code or assets are included.

## Implemented

| Feature | What it actually does | Verification |
| --- | --- | --- |
| Java startup recovery | Guarded attach/onCreate/late-entry fallbacks and up to six settings reads; independent authenticated startup checkpoint | Policy tests; no native runtime delivery and no physical-device success claimed |
| Java spring adapter | Guarded open/close spring-provider hook with damping/response controls | Structural fixtures and UI tested; real launcher and HyperOS 4 unverified; see [compatibility](SPRING-COMPATIBILITY.md) |
| Historical spring reports | Authenticated launcher state, build/settings matching, timestamp | Not a live active badge; APPLIED means parameter interception, not visual verification |
| Build identity | Version/commit in UI; distinct APK name, checksums, package/signature metadata | CI verifies APK signing; debug keys may differ between runs |
| Floating navigation | Home (spring controls) / Animator / Device; tappable destinations, bounded draggable circular lens | Geometry, touch, accessibility actions and API 32 fallback tested; physical TalkBack/visual testing pending |
| App-local glass optics | Actual underlying content capture; AGSL blur/displacement/highlights on API 33+, blurred bitmap/magnifying fallback on API 31/32 | Skia shader compilation/sampling check; not Apple’s renderer or launcher/system-wide glass |
| Launcher duration control | Opt-in multipliers on `ValueAnimator.setDuration(long)` in `com.miui.home` main process | JVM math/policy tests; actual HyperOS effects need device testing |
| Easing presets | Original, smooth, emphasized/liquid-glide, and linear via `setInterpolator` | Curve-only configuration at 1× supported; device effects unverified |
| Optional delay scaling | Applies duration multiplier to explicit `setStartDelay` calls | Off by default |
| Launcher restart | Confirmation + root; fixed launcher-only force-stop/relaunch command | Owner profile, default Home, timeout, error checks; device testing required |
| Device/build report | Locally display/copy Android build and launcher version | No automatic upload or claim of HyperOS verification |
| Duration presets | 0.5×, 0.75×, 1×, 1.5× | Settings persisted privately; restart launcher or reboot to apply |
| Restore defaults | Saves disabled / 1× / original curve / unscaled delays | Launcher restart or reboot required to remove a running process's existing hook |
| Settings UI | Plain black background, translucent rounded panels, edge highlights, ripple/soft-press buttons | Native Android views; live app-local glass only in floating navigation |
| In-app motion | Staggered entry and a replayable icon-to-card illustration | Local only; never used as proof the launcher hook works |
| Reduced-motion behavior | Skips entry motion and shows a still preview when system animator duration scale is zero | Device accessibility verification still needed |

The preview illustrates **the selected duration, easing preset, and optional delay scaling**. Original easing uses demonstration curves; selected presets are also wired to the launcher setter hook but do not simulate private HyperOS springs. It works with the module disabled and outside LSPosed. It is not a live recording or simulation of private launcher internals.

## Planned / not implemented

| Area | Candidate controls | Prerequisite |
| --- | --- | --- |
| Home screen | Grid, dock, spacing, icon size/labels, free placement | Inspect actual launcher layout APIs and persistence |
| Folders | Grid, popup size, visual treatment, open/close motion | Identify folder implementation in supported launcher builds |
| Recents / task switcher | Layout styles, card size/labels, switching behavior | Determine launcher vs system ownership; do not broaden scope blindly |
| Gestures | Home gesture behavior, swipe response, navigation motion | Inspect input ownership, cancellation, and accessibility behavior |
| Advanced animation | Native spring engines, custom curve editing, genie/cup effects, per-app profiles | Identify and version-gate private animation/remote-transition APIs |
| DIY-style arrangement | More flexible element placement | Investigate layout persistence, widgets, touch targets, and safe reset |
| Backup / restore | Layout snapshots and recovery | Validate schema compatibility and transactional restore before any writes |
| Share panel | Sharing layout/options | Research only; may be outside launcher scope or infeasible |

These rows are intentionally informational, with no fake switches or saved-but-unused settings. A feature is not implemented just because its name appears in the interface.

## Information needed for the next real launcher hook

- Device model and Android version.
- Full HyperOS build identifier, not just the marketing version.
- Xiaomi System Launcher (`com.miui.home`) version.
- A copy of that launcher's APK for method/signature inspection, if you can provide it.
- LSPosed version and a recoverable test device.

Do not include account credentials, device identifiers, or personal logs. The project does not download or collect this information automatically.

## Acceptance criteria for each future control

1. Identify the real hook point and its owner; document inspected versions.
2. Gate unsupported builds/signatures and fail safely with an actionable log.
3. Keep behavior off by default, including after settings corruption.
4. Implement an actual settings-to-hook path and test it; no decorative functionality.
5. Verify enable, disable, reboot, reset, and recovery on documented device builds.
6. Check large fonts, screen readers, system animation-off behavior, and normal launcher navigation.
7. Update the supported-build matrix only after device verification.

Changes to another app's database or system settings require a separate backup/recovery design. The current implementation does neither.
