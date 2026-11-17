# Privacy Policy — CHRONOS

**Effective date:** 18 November 2026
**App:** CHRONOS (`com.chronos.clock`)
**Developer:** Yash7672 (see [github.com/Yash7672](https://github.com/Yash7672))

## Summary

CHRONOS is an offline clock app. **It does not collect, transmit, store, sell, or share any personal or device data.** It has no internet access — the app does not even declare the `INTERNET` permission, so it is technically incapable of sending data off your device.

## Data the app does NOT collect

CHRONOS does not collect or share:

- Name, email, account, or login information (there is no account or sign-in)
- Location, contacts, calendar, photos, files, or media
- Device identifiers, advertising IDs, or usage analytics
- Crash reports, telemetry, or diagnostics
- Any data for advertising, marketing, or profiling

There are no third-party analytics, SDKs, ads, or trackers in this app.

## Data that exists only on your device (never transmitted)

| Data | Where it lives | Lifetime | Leaves the device? |
| ---- | -------------- | -------- | ------------------ |
| Clock & appearance settings (12/24h, theme, star density, modes) | Android DataStore (app-private storage) | Until you clear app data or uninstall | **No** |
| Notification **icons and titles** shown around the clock | Device RAM only | Current session; cleared when the app closes or the listener disconnects | **No** |
| Overlay (floating clock) position/enable state | App-private settings | Until you disable it or uninstall | **No** |

Notification content is never written to disk, never logged, never uploaded, and never backed up (`android:allowBackup="false"`).

## Permissions and why they exist

All special permissions are **optional and user-granted** through Android system screens:

- **Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`)** — optional. Used only to display the small app icons of your notifications around the clock. CHRONOS reads icon/title/package only; it does not read message bodies for storage or transmission, does not dismiss notifications unless you ask it to, and does not persist content.
- **Display over other apps (`SYSTEM_ALERT_WINDOW`)** — optional. Powers the floating AMOLED clock when you enable it in Settings.
- **Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`)** — required by Android only while the optional overlay is running, so the system keeps it alive and shows its own persistent notification.
- **Post notifications (`POST_NOTIFICATIONS`)** — optional; only so the overlay's own service notification can be shown on Android 13+.

CHRONOS requests **no** camera, microphone, location, contacts, storage, phone, or SMS permissions, and **no** internet permission.

## Children's privacy

CHRONOS is a general-audience utility and is not directed at children under 13. Because it collects no data at all, no personal information is knowingly collected from anyone, including children.

## Changes to this policy

If the app ever gains a data practice, this policy will be updated before that change ships, with a new effective date. Given the app's design (no network permission), any such change would also be visible in the manifest.

## Contact

Questions about privacy: open a discussion on [github.com/Yash7672/time](https://github.com/Yash7672/time) or a private security report as described in [SECURITY.md](SECURITY.md).
