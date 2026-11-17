# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

Only the latest release on [`main`](https://github.com/Yash7672/time) receives security fixes. Always install the newest APK from the [Releases](https://github.com/Yash7672/time/releases) page.

## Reporting a Vulnerability

Please **do not** open a public issue for security vulnerabilities.

Report privately through GitHub: **Security → Report a vulnerability** (private vulnerability reporting), or open a [private security advisory](https://github.com/Yash7672/time/security/advisories/new).

Include:

- What the issue is and where it occurs (activity, service, manifest entry…)
- Steps to reproduce
- Affected version / APK build
- Impact (e.g. data exposure, permission bypass, notification content leak)

You will get an acknowledgement within 7 days and a status update within 30 days. Confirmed issues are fixed before the next release and credited in the release notes unless you prefer to stay anonymous.

## Scope & Threat Model

CHRONOS is an offline, single-purpose clock. The design assumptions:

- **No network surface.** The app declares no `INTERNET` permission; it cannot send data anywhere. Cleartext traffic is disabled (`android:usesCleartextTraffic="false"`).
- **No backup surface.** `android:allowBackup="false"` — notification data and preferences are excluded from Android auto-backup and device-to-device transfer.
- **Notification content is ephemeral.** The listener keeps icons/titles in memory only for the current session; nothing is written to disk, databases, or logs, and nothing is uploaded (impossible without the network permission).
- **Components are not exported** unless the system requires it (`exported="true"` only on the launcher activity and the notification-listener service, which is guarded by the signature-level `BIND_NOTIFICATION_LISTENER_SERVICE` permission).
- **Release builds are minified and resource-shrunk** (R8), signed with a permanent release key, and contain no debug surfaces.

In scope for reports: any path that exfiltrates or persists notification content, bypasses a permission guard, allows a third-party app to drive CHRONOS services, or leaks data through backup/logcat.

Out of scope: Android/OEM platform behaviour (MIUI/HyperOS floating-window blocks, Play Protect heuristics), social-engineering of the user into granting special access, and issues in dependency upstreams already reported there.
