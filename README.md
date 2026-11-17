# CHRONOS

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
  after 30 s of inactivity (resumes instantly on touch).
- Static modes draw **exactly one frame** — no frame loop at all.
- No allocations in the draw loop: particles are precomputed per settings change.
- `android:keepScreenOn` is **off by default** — no unnecessary screen wake-ups.
- Honours the Android battery-saver state (read-only) and collapses effects.

### Smart notifications (privacy-first)
- Optional **notification-listener** integration: shows one original app icon
  per app around the clock with an unread dot/count.
- Access is requested **only** when you enable the feature, via Android's own
  consent screen. CHRONOS cannot and does not grant itself access.
- Notification content lives in **process memory only** — never written to disk,
  never uploaded (the APK declares **no internet permission**), cleared on exit.
- Deduplicated by Android key, group summaries collapsed into children, bounded
  to 50 entries / 8 visible icons.
- **Quick reply** via the source app's own `RemoteInput` action (long-press an
  icon) — the officially supported Android mechanism.
- **Bubbles**: CHRONOS forwards the source notification's bubble intent when the
  app provides one. See limitations below.

### Settings (persisted with Jetpack DataStore)
1. **Clock** — format, seconds, date, day, size, brightness
2. **Appearance** — 6 themes (incl. Light), the 3 AMOLED modes, burn-in drift,
   minimal clock, window brightness
3. **Star background** — enable, density (Low/Med/High), speed, twinkle
4. **Notifications** — access toggle, app selection, icons, indicators,
   privacy mode, quick reply, bubbles
5. **Display** — keep-awake, fullscreen, rotation, floating overlay, brightness
6. **Performance** — battery-friendly, battery saver, reduce animations,
   pause-when-inactive, lightweight rendering
7. **About** — version, privacy statement, licences, GitHub link, known
   Android limitations

### UI
- Portrait and landscape layouts (auto-rotates by default; can be locked).
- Settings gear sits **bottom-left, hidden by default** — tap anywhere to reveal
  it with a fade; it auto-hides after 3 seconds of inactivity.

---

## Known Android limitations (honest notes)

- **Floating windows / bubbles**: whether WhatsApp or any app appears as a
  bubble or floating window is controlled by **Android and that app**, not by
  CHRONOS. CHRONOS can only forward the notification's own bubble/content
  intent; it cannot force a third-party app into a floating window.
- **Notification icons**: Android does not allow one app to recolour or replace
  another app's notification icon. CHRONOS always shows the **original**
  application icon.
- **Quick replies** work only where the source app declares a `RemoteInput`
  reply action.
- **Overlay**: the optional floating clock needs the "display over other apps"
  special access; Android grants it per-app and it can be revoked at any time.
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

### Release APK (unsigned fallback → debug-signed)

```powershell
.\gradlew.bat :app:assembleRelease
```

Release builds are minified (`isMinifyEnabled` + resource shrinking) with
ProGuard rules in `app/proguard-rules.pro`.

### Release signing (never commit secrets)

Create `keystore.properties` in the repo root (it is git-ignored):

```properties
storeFile=release.jks
storePassword=***
keyAlias=chronos
keyPassword=***
```

If the file is absent, the release build falls back to the debug key so CI
always produces an installable APK.

---

## Continuous integration

`.github/workflows/android.yml` runs on every push to `main` and on version
tags:

1. Sets up JDK 17 + Android SDK
2. Runs unit tests
3. Runs lint
4. Builds a release APK and uploads it as an artifact
5. On a `v*` tag, attaches the APK to a GitHub Release

Release signing in CI uses secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`) — if they are not configured the workflow still
produces a debug-signed, installable APK. No secrets are committed.

---

## Project structure

```
app/src/main/java/com/chronos/clock/
├── ChronosApp.kt              # Application + manual service container
├── MainActivity.kt            # Display flags (awake/fullscreen/rotation/brightness)
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
├── services/OverlayService.kt       # optional floating clock
└── ui/components/SettingsRows.kt
```

DI note: CHRONOS uses a ~30-line manual `AppContainer` instead of Hilt — the
object graph is three singletons, and skipping a DI compiler plugin keeps cold
start and build time lower.

---

## Privacy

- No account, login, analytics, advertising, or crash reporting.
- **No internet permission is declared** — CHRONOS cannot send data anywhere.
- No contacts, location, microphone, camera, or storage access.
- Notification content is memory-only and never persisted.
- Settings are stored locally via Jetpack DataStore.

## Licence

Source code released under the [MIT Licence](LICENSE). Built with AndroidX,
Jetpack Compose, Material 3, Kotlin, Coroutines, and DataStore (Apache 2.0).
