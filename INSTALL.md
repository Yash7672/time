# Installing CHRONOS on a phone (and the Play Protect block)

CHRONOS is distributed as a signed APK outside Google Play and uses Android's
**notification access**. Google treats notification access as a sensitive
permission, so **Play Protect automatically blocks the install** whenever the
APK is opened from a browser, messenger or file manager:

> **App blocked to protect your device** — *This app can request access to
> sensitive data. This can increase the risk of identity theft or financial
> fraud.*

This is Google's policy for **internet-sideloading sources**
([official guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)),
not a virus verdict on CHRONOS. It cannot be switched off from inside the app —
the only thing that would remove the trigger is deleting the notification
feature. Every method below installs the exact same APK.

## Why this APK is safe to trust

Verified on the current release build:

- **No INTERNET permission** — the app physically cannot send data anywhere.
- **No runtime permissions at all.** The merged APK requests exactly one
  permission, AndroidX's internal signature-level
  `com.chronos.clock.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which only
  guards the app's own receivers and grants nothing outside it.
- No accessibility, overlay, storage, contacts, location, camera, microphone
  or SMS access.
- The only special access is **notification access**, which Android grants
  only after you flip the switch on its own consent screen.
- Notification content lives in **RAM only** — never written to disk, never
  uploaded, gone when the process dies.
- Every release is signed with the **same certificate**:

  ```
  CN=CHRONOS, O=Yash7672
  SHA-256: b3c9eec2bb1909bc4ad2cdf16933ccb728fc4b45fe5e8c7c6d5e3c0e83fe01b1
  ```

  Check any copy before installing:

  ```powershell
  apksigner verify --print-certs app-release.apk
  ```

  If the certificate differs, do not install it.

## Method A — one click over USB (recommended: no warning, no toggles)

Needs a Windows PC, a USB cable and
[Android Platform Tools](https://developer.android.com/tools/releases/platform-tools).

1. On the phone: **Settings → About phone → tap Build number 7×**, then
   **Developer options → enable USB debugging**.
2. Connect the phone, unlock it, and accept **Allow USB debugging**.
3. Run `install-chronos.bat` (repo root or next to the APK). It installs
   `apk\release\app-release.apk` and opens CHRONOS.

ADB installs are not internet-sideloading installs, so Play Protect does not
block them. Updates install in place — settings and grant are kept.

macOS / Linux:

```bash
adb install -r apk/release/app-release.apk
```

## Method B — on the phone, temporarily turn off scanning

Only if no PC is available:

1. Open **Google Play Store** → tap your profile picture → **Play Protect**.
2. Tap the **gear** (top right) → turn off **Scan apps with Play Protect**.
3. Open the APK from Downloads / the file manager and install it; tap
   **Allow** if asked.
4. **Turn scanning back on immediately after the install.** Re-enabling it
   does not uninstall CHRONOS, and Play Protect keeps working normally.

## Method C — "More details" → "Install anyway"

Some devices and Play Protect versions offer a bypass in the block dialog:
tap **More details** (or the ⋮ menu) → **Install anyway**, then confirm with
your PIN or fingerprint. When that button is absent, use Method A or B.

## Method D — permanent, zero warnings for everyone: Google Play

Play installs are exempt from this protection. Internal or closed testing is
enough, so nobody who installs from Play ever sees the dialog. The AAB is built
by CI, the privacy policy and every Play Console answer already exist — see
[docs/PLAY_STORE.md](docs/PLAY_STORE.md). This is the only permanent fix that
removes the dialog for all users without touching their device settings.

## Method E — free appeal to Google (clears the block for this APK)

Google reviews false positives through the official appeal form:

1. Upload the **exact APK you distribute** to
   [VirusTotal](https://www.virustotal.com) and let it finish scanning.
2. Copy its **SHA-256** from the Details tab.
3. Submit the
   [Play Protect Appeals Submission Form](https://support.google.com/googleplay/android-developer/contact/protectappeals)
   with:
   - package name: `com.chronos.clock`
   - the VirusTotal SHA-256
   - a short factual note, for example: *CHRONOS is an offline clock and
     notification-icon app. The APK declares no INTERNET permission and no
     runtime permissions; its only special access is Android's
     notification-listener service, which the user grants manually. It does
     not use accessibility services or read SMS.*
4. Decisions are final and Google does not answer every submission. An
   approved appeal removes the block for that APK — no device changes needed
   for anyone who installs it.

## What none of this changes

- The block is decided by Google Play Services **before CHRONOS code runs**, so
  the app itself cannot bypass, whitelist or hide it.
- Nothing here changes how CHRONOS behaves after install.
