# CHRONOS

[![Android CI](https://github.com/Yash7672/time/actions/workflows/android.yml/badge.svg)](https://github.com/Yash7672/time/actions/workflows/android.yml)
[![CodeQL](https://github.com/Yash7672/time/actions/workflows/codeql.yml/badge.svg)](https://github.com/Yash7672/time/actions/workflows/codeql.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)](#building)

**An AMOLED space clock for Android.** Pure-black background, a precise live
clock, a slow dim starfield, and privacy-first notification icons — built for
maximum battery efficiency on OLED displays.

<p align="center">
  <code>Kotlin · Jetpack Compose · MVVM · DataStore · minSdk 26 · targetSdk 36</code>
</p>

---

## Features

### Clock
- **Accurate HH:MM:SS** with second-boundary-aligned ticks — updates fire exactly
  when the wall clock rolls over, not on a drifting timer.
- **Lifecycle-aware**: ticks stop the moment the app is not visible (zero work in
  the background) and resync immediately on resume, after a time-zone change, a
  DST transition, or a manual system-clock change.
- 12/24-hour format, optional seconds, date and day-of-week header, adjustable
  size, and a dimming slider. All digits use **tabular figures** (`tnum`) so the
  clock never shifts horizontally while seconds change.

### AMOLED display modes (three, per spec)
| Mode | Behaviour |
|---|---|
| **Pure AMOLED** | Completely black screen, minimal dim-white clock, no animation. |
| **Space AMOLED** | Black background with very slow, tiny, dim stars. |
| **Ultra Battery** | Static black screen, minimal clock, reduced visual updates, no effects. |

All three keep clock accuracy independent of visual effects.

### AMOLED-optimised background
- Primary background is **#000000** in every dark theme — most pixels stay off.
- No gradients, no glow, no large bright surfaces. Text is soft white/grey
  (#E6E8EC / #B8BCC4), never full-bright #FFFFFF.
- Stars are **tiny (0.6–1.8 px), dim (alpha 0.18–0.55)** with gentle sine
  twinkle; white/blue/purple in a 80/12/8 mix.
- Particle counts are hard-capped (60/120/220 by density, ≤300 absolute).
- Update loop is throttled to **~15 fps** (5 fps battery-friendly) instead of
  60 fps — slow drift does not need vsync.
- **Burn-in protection**: the whole clock block drifts 1–2 px every 10 minutes
  through a deterministic cycle. Imperceptible, never affects readability.
- **Minimal clock mode**: always-on-display style with fewer lit pixels.
- Window-level brightness override (never writes system brightness).

### Battery-first engineering
- **No background service for the clock**, no wake lock, no WorkManager
  polling, no location, no network — the clock only ticks while visible.
- Star animation pauses when the app isn't visible (`repeatOnLifecycle`) and
  optionally after 30 s of inactivity (off by default — the sky keeps drifting,
  resumes instantly on touch).
- Static modes draw **exactly one frame** — no frame loop at all.
- No allocations in the draw loop: particles are precomputed per settings change.
- `android:keepScreenOn` is **off by default** — no unnecessary screen wake-ups.
- Honours the Android battery-saver state (read-only) and collapses effects.

### Smart notifications (privacy-first)
- Optional **notification-listener** integration: shows one original app icon
  per app along the bottom of the clock screen with an unread dot/count.
  Tap an icon to open that app over CHRONOS — on **iQOO/vivo phones** that
  attempts the **native Small Window** automatically (the real floating
  window on Funtouch OS), on freeform-capable devices a floating
  `setLaunchBounds` window, otherwise **split screen** beside it;
  long-press opens the quick-reply sheet, which shows the sender and
  message you are replying to (or dismisses the notification when the app
  offers no reply action). The integration setting
  itself is **on by default** — the only thing Android requires is the
  notification-access grant, which CHRONOS can never give itself.
- Access is requested **only** via Android's own consent screen — CHRONOS
  cannot and does not grant itself access. It opens that screen on **every
  launch until you grant it** (one switch flip; after that it never appears
  again), and the bottom-centre prompt reopens it any time the grant is
  missing; the feature then switches on automatically. Whenever CHRONOS
  opens with access granted it also re-binds the listener and backfills
  whatever is currently on screen, so icons appear immediately — even for
  notifications that arrived while the app was closed. Every listener
  callback is exception-guarded (one malformed notification from any app
  can never take the service down), and if the system drops the binding
  the listener asks to be re-bound by itself.
- The bottom lane is **never silently empty**: whenever no icons render, a
  status line says exactly why — *"App icons are off — tap to turn on"*,
  *"Only N chosen apps allowed — tap to show all apps"*, or
  *"listening for notifications…"* — with a one-tap fix where possible.
- Notification content lives in **process memory only** — never written to disk,
  never uploaded (the APK declares **no internet permission**), cleared on exit.
- Deduplicated by Android key, group summaries collapsed into children, bounded
  to 50 entries / 8 visible icons.
- **Quick reply** via the source app's own `RemoteInput` action (long-press an
  icon → reply sheet listing **all** your last unseen messages — sender, text
  and time, expanding multi-message (MessagingStyle) notifications into every
  message — with Send / Dismiss) — the officially supported Android mechanism.
- **Bubbles**: if an app cannot be launched normally, CHRONOS falls back to the
  source notification's own bubble/content handle (the *Prefer bubbles* switch
  in Settings decides). See limitations below.
- **Floating App Windows** (`FloatingAppLauncher`, **public Android APIs
  only — no reflection, no hidden windowing APIs, no accessibility
  automation, no fake windows**): tapping an icon follows a per-device
  ladder —
  - **iQOO / vivo (Funtouch OS / OriginOS)**: the native **Small Window**
    is the real floating window there (rounded corners, title-bar
    minimize/maximize/close, drag to move, drag corners to resize, Mini
    Small Window mode). CHRONOS attempts it **automatically on tap**:
    public `ActivityOptions.setLaunchBounds` plus the AOSP launch-options
    windowing-mode key (`android.activity.windowingMode`, written with a
    plain `Bundle.putInt` — the same data `am start --windowingMode 5`
    sends; no reflection, no hidden method, no permission gate on that key
    in `SafeActivityOptions`). If the system refuses every route, the
    **guided Recents workflow** (swipe up & hold → arrow next to the app
    name → *Small window*) appears as a last resort — and only then.
  - **Freeform-capable devices** (advertise
    `FEATURE_FREEFORM_WINDOW_MANAGEMENT` or have the system's
    `enable_freeform_support` switch set — the Developer-options/adb
    toggle): the same public bounded freeform attempt, then split screen.
  - **Everything else**: split screen → normal full-screen → the source
    notification's own bubble/content handle.

  Every step degrades to the next; CHRONOS itself never closes. The ladder
  is driven by Settings → Notifications → Floating App Windows and is
  unit-tested (`FloatingAppLauncherTest`). When nothing could be opened —
  or floating was blocked on a device with no floating support — an honest
  dismissible dialog says what actually opened instead, never a floating
  success that did not happen; it can be switched off with *Show
  instructions for unsupported devices*, and a working tap on a Small
  Window phone never shows a dialog.

### Settings (persisted with Jetpack DataStore)
1. **Clock** — format, seconds, date, day, size, brightness
2. **Appearance** — 6 themes (incl. Light), clock digit colour, the 3 AMOLED
   modes, burn-in drift, minimal clock, window brightness
3. **Star background** — enable, density (Low/Med/High), speed, twinkle
4. **Notifications** — access toggle, app selection, icons, indicators,
   privacy mode, quick reply, bubbles, **floating app windows** (enable
   floating-window launch, prefer floating when supported, allow normal
   full-screen fallback, show instructions for unsupported devices)
5. **Display** — auto brightness (light sensor), keep-awake, fullscreen
6. **Performance** — battery saver, pause stars when idle (star throttling and
   low-fps rendering are automatic)
7. **About** — version, privacy statement, licences, GitHub link, known
   Android limitations

### UI
- Portrait and landscape layouts: auto-rotates with the sensor **while the
  app is open** and never changes your system rotation setting.
- Settings gear sits **bottom-left, hidden by default** — tap anywhere to reveal
  it with a fade; it auto-hides after 3 seconds of inactivity.

---

## Known Android limitations (honest notes)

- **Floating windows / bubbles**: whether WhatsApp or any app appears as a
  bubble or floating window is controlled by **Android and that app**, not by
  CHRONOS. It attempts only officially supported routes — the source
  notification's own bubble/content intent, the public bounded freeform
  launch (window bounds + AOSP windowing-mode option, on devices where
  freeform is allowed: the feature flag or the `enable_freeform_support`
  Developer-options switch), split screen, and — as a last resort on
  iQOO/vivo — the guided native Small Window workflow — then falls back to
  a normal launch. It cannot force a third-party app into a floating window
  on devices that do not offer one, and Funtouch OS only lists apps it
  supports for Small window.
- **Notification icons**: Android does not allow one app to recolour or replace
  another app's notification icon. CHRONOS always shows the **original**
  application icon.
- **Quick replies** work only where the source app declares a `RemoteInput`
  reply action.
- **Battery claims**: no specific percentage improvement is claimed — those
  numbers require measurement on a real AMOLED device, and this README does not
  fabricate them. The design choices above are the measurable levers.

---

## Building

### Prerequisites
- JDK 17+ (JDK 21/25 also work)
- Android SDK with **platform android-36** and build-tools 36.x
- No IDE required — the Gradle wrapper is included

### Debug APK

```powershell
.\gradlew.bat :app:assembleDebug
# output: app\build\outputs\apk\debug\app-debug.apk
```

### Tests & lint

```powershell
.\gradlew.bat :app:testDebugUnitTest   # 23 unit tests
.\gradlew.bat :app:lintDebug           # 0 errors
```

### Release APK

```powershell
.\gradlew.bat :app:assembleRelease   # APK:  app/build/outputs/apk/release/
.\gradlew.bat :app:bundleRelease     # AAB:  app/build/outputs/bundle/release/ (Play Store)
```

Release builds are minified (`isMinifyEnabled` + resource shrinking) with
ProGuard rules in `app/proguard-rules.pro`.

### Release signing (never commit secrets)

`keystore.properties` in the repo root (git-ignored, already present locally)
holds the permanent release key:

```properties
storeFile=chronos-release.jks
storePassword=***
keyAlias=chronos
keyPassword=***
```

Verify a build is release-signed (must **not** say `CN=Android Debug`):

```powershell
apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk
```

If `keystore.properties` is absent (fresh CI checkout without secrets), the
release build falls back to the debug key so an installable APK always exists —
but **never ship that fallback**: sideloads signed with different keys cannot be
installed over each other, and debug certificates trigger OEM/Play Protect
"app risk" warnings. Back up `chronos-release.jks` + `keystore.properties` in a
password manager — losing them means losing the ability to update the app.

---

## Continuous integration

`.github/workflows/android.yml` runs on every push to `main`, on PRs, and on
version tags:

1. JDK 17 (Temurin) + Android SDK (`platforms;android-36`, `build-tools;36.0.0`)
2. `bash gradlew :app:testDebugUnitTest` — unit tests
3. `bash gradlew :app:lintDebug` — lint (must stay at 0 errors)
4. `bash gradlew :app:assembleRelease :app:bundleRelease` — signed APK + Play AAB
5. `bash gradlew :app:assembleDebug` — debug APK for testers
6. Uploads reports + all three artifacts; on a `v*` tag, attaches APK + AAB to
   a GitHub Release

Hardening: every action is **pinned to a full commit SHA**, workflow tokens use
**least-privilege permissions** (`contents: read`; only the release job gets
`contents: write`), Gradle wrapper checksums are **validated**, and
[Dependabot](.github/dependabot.yml) keeps actions + Gradle dependencies
updated. A separate [CodeQL](.github/workflows/codeql.yml) workflow scans the
Kotlin sources on every push, PR, and weekly.

Release signing in CI uses secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`) — if they are not configured the workflow still
produces a debug-signed, installable APK. No secrets are committed. See
[docs/PLAY_STORE.md](docs/PLAY_STORE.md) §1 for how to set them:

```powershell
# prints the value to paste into GitHub → Settings → Secrets and variables → Actions
[Convert]::ToBase64String([IO.File]::ReadAllBytes("chronos-release.jks"))
Get-Content keystore.properties   # storePassword / keyAlias / keyPassword
```

## Security & policy compliance

| Requirement | Where |
| ----------- | ----- |
| GitHub security policy (vulnerability reporting) | [SECURITY.md](SECURITY.md) |
| Privacy policy (Play Store URL) | [PRIVACY_POLICY.md](PRIVACY_POLICY.md) |
| Play Console: Data safety, permission declarations, AAB | [docs/PLAY_STORE.md](docs/PLAY_STORE.md) |
| Contribution rules | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Issue & PR templates | [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/) · [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md) |
| Code scanning (Kotlin) | [.github/workflows/codeql.yml](.github/workflows/codeql.yml) |
| Dependency updates | [.github/dependabot.yml](.github/dependabot.yml) |
| Supply-chain: SHA-pinned actions, wrapper validation, least-privilege tokens | [.github/workflows/android.yml](.github/workflows/android.yml) |
| Manifest hardening: no backup, no cleartext, explicit `exported`, signature-guarded listener, `specialUse` FGS subtype, immutable intents | `app/src/main/AndroidManifest.xml` |

---

## Project structure

```
app/src/main/java/com/chronos/clock/
├── ChronosApp.kt              # Application + manual service container
├── MainActivity.kt            # Display flags, sensor rotation, auto brightness
├── core/
│   ├── constants/AppConstants.kt
│   ├── lifecycle/ClockTicker.kt     # second-boundary-aligned tick source
│   ├── permissions/PermissionHelper.kt
│   └── utils/TimeUtils.kt           # pure formatting (unit-tested)
├── data/
│   ├── preferences/SettingsDataStore.kt
│   └── repository/SettingsRepositoryImpl.kt
├── domain/
│   ├── model/                       # Clock/Theme/Star/Notification/Display/Performance
│   └── repository/SettingsRepository.kt
├── navigation/AppNavigation.kt
├── notifications/
│   ├── NotificationListener.kt      # NotificationListenerService
│   ├── NotificationRepository.kt    # in-memory store + icon cache
│   ├── NotificationReducer.kt       # dedup/group/filter (unit-tested)
│   ├── NotificationManager.kt       # open/dismiss/reply facade
│   └── QuickReplyHandler.kt         # RemoteInput sender
├── presentation/
│   ├── background/StarFieldCanvas.kt + StarParticle.kt
│   ├── home/                        # HomeScreen, ClockView, DateView, icons, gear
│   ├── settings/                    # 7 sections + app picker
│   └── theme/                       # AMOLED palette, theme, typography
└── ui/components/SettingsRows.kt
```

DI note: CHRONOS uses a ~30-line manual `AppContainer` instead of Hilt — the
object graph is three singletons, and skipping a DI compiler plugin keeps cold
start and build time lower.

---

## Privacy

Full policy: [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

- No account, login, analytics, advertising, or crash reporting.
- **No internet permission is declared** — CHRONOS cannot send data anywhere.
- No contacts, location, microphone, camera, or storage access.
- Notification content is memory-only and never persisted.
- Settings are stored locally via Jetpack DataStore.
- `android:allowBackup="false"` — nothing is backed up or transferred to a new device.

## Licence

Source code released under the [MIT Licence](LICENSE). Built with AndroidX,
Jetpack Compose, Material 3, Kotlin, Coroutines, and DataStore (Apache 2.0).
