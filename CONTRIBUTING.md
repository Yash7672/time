# Contributing to CHRONOS

Thanks for helping improve CHRONOS. This project is deliberately small: an offline AMOLED clock with an optional notification listener. Keep changes aligned with that scope.

## Ground rules

- **No new permissions without discussion.** Every permission is documented in `docs/PLAY_STORE.md`. Opening an issue first avoids wasted work.
- **No network code.** The app must never gain `INTERNET`. Any change that needs connectivity belongs in a separate app.
- **Battery first.** No polling loops, no wake locks, no work that runs while the app is invisible. The clock ticks on second boundaries only while visible.
- **Honest claims.** Never write battery percentages, "X% faster", or similar numbers that were not measured on a real device.
- **No secrets in the repo.** Keystores, passwords, and tokens stay out of git (see `.gitignore`). CI signing uses GitHub Actions secrets.

## Development setup

Requirements: JDK 17+, Android SDK with platform `android-36`, Android Studio or command line only.

```bash
# Windows
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Linux / macOS (wrapper is committed executable, 100755)
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

GitHub Actions runs the same three tasks plus `assembleRelease` and `bundleRelease` on every push to `main`.

## Pull requests

1. Fork and branch from `main` (`feature/…` or `fix/…`).
2. Keep the diff focused — one concern per PR.
3. Tests: add or update a JUnit test under `app/src/test/` for logic changes (reducers, time formatting, scheduling).
4. `:app:lintDebug` must stay at **0 errors**, `:app:testDebugUnitTest` must pass.
5. Update `README.md` / `docs/` if behaviour, permissions, or privacy handling changes.
6. Fill in the PR template.

## Style

- Kotlin, Jetpack Compose, MVVM, StateFlow, manual DI (no Hilt).
- 4-space indent, trailing commas in argument lists, KDoc on public API.
- Comments explain *why*, not *what*.
- String user-facing text goes in `res/values/strings.xml`.

## Reporting bugs

Use the **Bug report** issue template and attach the app version (`Settings → About`), device/OS build, and steps to reproduce. For security issues see [SECURITY.md](SECURITY.md) — report privately, not in issues.
