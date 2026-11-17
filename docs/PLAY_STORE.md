# Play Store Submission Guide — CHRONOS

How to publish CHRONOS on Google Play and what to enter for every policy form. Keep this file in sync with `AndroidManifest.xml` and `PRIVACY_POLICY.md`.

## 1. Build artifacts

CI produces both required formats on every tag `v*`:

| Artifact | Path | Purpose |
| -------- | ---- | ------- |
| AAB | `app-release.aab` (job artifact `chronos-release-aab`) | **Required upload format** for Play Console |
| APK | `app-release.apk` (job artifact `chronos-release-apk`) | Direct install / GitHub Releases |

Both are signed with the permanent release keystore. For Play, enroll in **Play App Signing** (Setup → App signing) and upload the AAB signed with your *upload key* — Google then manages the app-signing key. Keep the upload key backed up off-device (password manager + encrypted copy); losing it blocks future uploads.

## 2. App identity

- **Package name:** `com.chronos.clock` — permanent; cannot be changed after first publish.
- **Version:** `versionName 1.0.0`, `versionCode 1` (auto-increment per release in `app/build.gradle.kts`).
- **Target/compile SDK:** 36 (meets current Play target-API requirements).
- **App category:** Clock / Personalization. Rating: Everyone (no IARC triggers — no chat, no user content, no purchases).

## 3. Data safety form (Play Console → App content → Data safety)

Answer honestly — CHRONOS's answers are all negative because the app has no network permission:

| Question | Answer |
| -------- | ------ |
| Does your app collect or share any of the required user data types? | **No** |
| Is data encrypted in transit? | Not applicable — no data is collected or shared |
| Can users request data deletion? | Not applicable — no data is collected (state this if asked) |
| Account creation | No |
| Photos/video/audio, contacts, location, device IDs, app activity, crash logs, diagnostics | **None** |

Privacy policy URL (required because of the sensitive permissions below):
`https://github.com/Yash7672/time/blob/main/PRIVACY_POLICY.md`

## 4. Permission declarations

### Notification access (restricted)
Play Console → Policy → App permissions → **Notifications**:

> CHRONOS is an AMOLED clock that decorates its face with the small icons of the user's current notifications. It reads only the package name, app icon and title of posted notifications to render those icons in-app. Content is kept in memory for the visible session only — never written to storage, never logged, never transmitted (the app declares no INTERNET permission), never shared with third parties. It is not used for behavioral tracking, advertising, or data selling. Notification access is optional; the clock works fully without it and the user can revoke it at any time in system settings.

### Display over other apps (`SYSTEM_ALERT_WINDOW`)
> Optional floating clock. The user explicitly enables it in the app's Settings, is taken to the system toggle, and can disable it at any time. Used only to draw the clock itself over other apps; never observes or captures the screen content beneath it.

### Foreground service — special use (`FOREGROUND_SERVICE_SPECIAL_USE`)
> Required by Android 14+ to keep the user-enabled floating clock alive while the screen is on. The service shows a persistent, low-priority notification and draws only the clock. Manifest declares `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` accordingly. No data processing, no background location, no media playback.

### Post notifications (`POST_NOTIFICATIONS`)
> Only so the overlay service's own required notification can appear on Android 13+. No other notifications are sent.

## 5. Store listing assets

- **App icon 512×512 PNG** — export from `ic_launcher` adaptive icon foreground+background (`mipmap-anydpi-v26` uses `ic_launcher_background` (#000000) + vector foreground).
- **Feature graphic 1024×500** — optional for Games only; needed for the Play listing banner.
- **Screenshots:** at least 2 phone shots — clock face in space theme, settings panel. Optional: landscape shot (app supports auto-rotation).
- **Short description (80 chars):** `AMOLED space clock. Pure black, twinkling stars, smart notification icons.`
- **Full description:** reuse the README feature list; keep every claim verifiable (no battery percentages — see below).

## 6. Policy checklist before submitting

- [ ] AAB uploaded, signed with upload key (Play App Signing enrolled)
- [ ] Target API 36 (already set)
- [ ] Data safety form = all "No" answers above
- [ ] Privacy policy URL added and publicly reachable (raw GitHub link, no login)
- [ ] Notification-access use-case text submitted
- [ ] Overlay + special-use FGS justifications submitted
- [ ] Content rating questionnaire completed (Everyone)
- [ ] **No** misleading battery/performance percentages anywhere in listing or README (none are claimed — keep it that way unless measured on a real device)
- [ ] Store listing screenshots match the actual UI
- [ ] App does not request Play Protect bypass, accessibility for notification reading, or default-SMS/call-log/SMS permissions (it doesn't)

## 7. GitHub Releases (sideload path)

Every `v*` tag publishes `app-release.apk` + `app-release.aab` automatically via `.github/workflows/android.yml`. Sideloaded APKs are signed with the same permanent key, so installs/upgrades stay consistent across machines. Users must enable "install from unknown sources" for the browser/file manager — that is an Android consent prompt, not a Play Protect failure; never instruct users to disable Play Protect.
