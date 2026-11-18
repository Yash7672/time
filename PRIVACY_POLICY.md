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
| Notification **icons and titles** shown on the clock screen | Device RAM only | Current session; cleared when the app closes or the listener disconnects | **No** |

Notification content is never written to disk, never logged, never uploaded, and never backed up (`android:allowBackup="false"`).

## Permissions and why they exist

The only special permission is **optional and user-granted** through an Android system screen:

- **Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`)** — optional. Used only to display the small app icons of your notifications on the clock screen. CHRONOS reads icon/title/package only; it does not read message bodies for storage or transmission, does not dismiss notifications unless you ask it to, and does not persist content.

CHRONOS requests **no** camera, microphone, location, contacts, storage, phone, or SMS permissions, **no** overlay (`SYSTEM_ALERT_WINDOW`), **no** foreground-service or post-notification permissions, and **no** internet permission.

**Sensors:** the ambient light sensor powers the optional auto-brightness feature (dim the clock in a dark room, brighten it in a bright room). It is read only while CHRONOS is open, used only to adjust this app's own window brightness, and the readings are never stored and never leave the device.

## Children's privacy

CHRONOS is a general-audience utility and is not directed at children under 13. Because it collects no data at all, no personal information is knowingly collected from anyone, including children.

## Changes to this policy

If the app ever gains a data practice, this policy will be updated before that change ships, with a new effective date. Given the app's design (no network permission), any such change would also be visible in the manifest.

## Contact

Questions about privacy: open a discussion on [github.com/Yash7672/time](https://github.com/Yash7672/time) or a private security report as described in [SECURITY.md](SECURITY.md).
