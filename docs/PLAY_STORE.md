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

### Ambient light sensor (no declaration required)
Auto brightness reads `TYPE_LIGHT` only while the app is visible, to dim/brighten the CHRONOS window itself (dark room → dim, bright room → bright). Readings are used on-device only, never stored and never transmitted. This needs no Play permission declaration (normal sensor, no dangerous permission).

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
- [ ] Content rating questionnaire completed (Everyone)
- [ ] **No** misleading battery/performance percentages anywhere in listing or README (none are claimed — keep it that way unless measured on a real device)
- [ ] Store listing screenshots match the actual UI
- [ ] App does not request Play Protect bypass, accessibility for notification reading, or default-SMS/call-log/SMS permissions (it doesn't)

## 7. GitHub Releases (sideload path)

Every `v*` tag publishes `app-release.apk` + `app-release.aab` automatically via `.github/workflows/android.yml`. Sideloaded APKs are signed with the same permanent key, so installs/upgrades stay consistent across machines.

**Play Protect and direct installs:** Play Protect auto-blocks direct installs of apps that use notification access when the APK comes from an internet-sideloading source (browser, messenger, file manager). This is a Google policy decision about the source, not a CHRONOS failure — no app can whitelist itself out of it. The zero-warning path is Play distribution (internal/closed testing is enough); otherwise the recipient can install over **ADB**, file a **Play Protect false-positive report** through Google Play support with the APK's SHA-256, or temporarily turn off *Scan apps with Play Protect* for the install. Never present disabling Play Protect as normal, permanent advice.
