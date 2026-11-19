package com.chronos.clock.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.chronos.clock.domain.model.NotificationItem
import com.chronos.clock.domain.model.NotificationSettings

/**
 * Opens other apps on top of CHRONOS using **public Android APIs only** —
 * no reflection, no hidden method calls, no accessibility gestures, no
 * fake windows, no overlay permission.
 *
 * Capability model (each device class gets its own honest path):
 *
 *  - **Freeform-capable devices** — advertise
 *    `FEATURE_FREEFORM_WINDOW_MANAGEMENT` or have the system's own
 *    freeform switch set (`Settings.Global "enable_freeform_support"` —
 *    the Developer-options/adb toggle; exactly the pair
 *    `ActivityTaskManagerService.retrieveSettings()` evaluates). A tap
 *    attempts a real freeform launch: public
 *    `ActivityOptions.setLaunchBounds` plus the AOSP launch-options
 *    windowing-mode key written with a plain `Bundle.putInt` — the same
 *    data `am start --windowingMode 5` sends, which
 *    `SafeActivityOptions.checkPermissions()` does not gate (verified
 *    against frameworks/base). Reflection is neither needed nor possible
 *    (Android 11+ blocks `setLaunchWindowingMode` reflection anyway).
 *  - **vivo / iQOO (Funtouch OS, OriginOS)** — the native **Small Window**
 *    is the real floating window on these phones (rounded corners, title
 *    bar with minimize/maximize/close, draggable, resizable, Mini Small
 *    Window mode), and Funtouch's small window is freeform-based. The
 *    same public attempt therefore runs **automatically on tap — no
 *    dialog, no instructions**. Only when the system refuses every launch
 *    route does [Step.GUIDE_SMALL_WINDOW] appear (a last resort), showing
 *    vivo's own Recents workflow. The system may ignore the windowing
 *    hint on devices where freeform is disabled — CHRONOS never claims a
 *    floating success it cannot verify and never blocks a working app
 *    open behind an explanation dialog.
 *  - **Everything else** — split screen → full-screen → the source
 *    notification's own handle, each step degrading to the next.
 *
 * The strategy ladder is a pure function of settings × capabilities
 * ([planFor]), unit-tested in `FloatingAppLauncherTest`.
 */
class FloatingAppLauncher(private val appContext: Context) {

    /** Which strategy actually opened the app (or what the tap produced). */
    enum class Outcome {
        /**
         * Started with freeform options (bounds + windowing mode). The
         * system accepted the launch; CHRONOS cannot observe the resulting
         * window mode, so nothing ever reports this as a *verified*
         * floating success.
         */
        FLOATING,
        /** Opened beside CHRONOS in split screen. */
        SPLIT,
        /** Opened with a plain full-screen launch. */
        FULL_SCREEN,
        /** Opened through the source notification's own PendingIntent. */
        NOTIFICATION_HANDLE,
        /**
         * Nothing launched: the guided Small Window walkthrough was shown
         * as a last resort (every launch route above it failed).
         */
        SMALL_WINDOW_GUIDE,
        /** Nothing was launched at all. */
        NONE,
    }

    /** Why the floating-window attempt could not be made. */
    enum class FloatBlockReason {
        /** No standard freeform support on this device. */
        DEVICE_UNSUPPORTED,
        /** Freeform exists but the system refused the bounded launch. */
        REFUSED,
    }

    /** One step of the launch ladder. */
    enum class Step { FLOAT, SPLIT, FULL_SCREEN, HANDLE, GUIDE_SMALL_WINDOW }

    /**
     * Device-side capabilities — pure input to [planFor] so tests can model
     * any device without a Context or the real Build properties.
     */
    data class Capabilities(
        /** Standard freeform allowed (feature flag or the system switch). */
        val freeform: Boolean,
        /** vivo/iQOO device shipping the native Small Window feature. */
        val smallWindow: Boolean,
    )

    /** Result of one icon tap. */
    data class LaunchResult(
        val outcome: Outcome,
        /** Non-null when floating was preferred but could not be attempted. */
        val floatBlockReason: FloatBlockReason? = null,
        /**
         * Whether the UI should explain this tap (checked against Settings →
         * "Show instructions" already). False for a Small Window phone that
         * got the app open — a working tap is never nagged with a dialog.
         */
        val showNotice: Boolean = false,
    ) {
        val launched: Boolean get() = outcome != Outcome.NONE
    }

    /**
     * Opens [item]'s app on top of CHRONOS following [planFor]. CHRONOS
     * itself is never closed: launches go through the application context
     * with FLAG_ACTIVITY_NEW_TASK, so the clock keeps running behind the
     * new window and closing that window returns to CHRONOS untouched.
     *
     * On vivo/iQOO this attempts the native Small Window automatically;
     * the guided walkthrough only surfaces (via [Outcome.SMALL_WINDOW_GUIDE])
     * when nothing could be opened at all.
     */
    fun open(item: NotificationItem, settings: NotificationSettings): LaunchResult {
        val caps = Capabilities(
            freeform = supportsFreeform(),
            smallWindow = hasNativeSmallWindow(),
        )
        val plan = planFor(settings, caps)

        // Honest block tracking: only when floating was explicitly PREFERRED
        // and the device has neither standard freeform nor the native
        // Small Window path — then (and only then) the UI explains it.
        var block: FloatBlockReason? = null
        if (settings.floatingWindowsEnabled &&
            settings.preferFloatingWindows &&
            !caps.freeform &&
            !caps.smallWindow
        ) {
            block = FloatBlockReason.DEVICE_UNSUPPORTED
        }

        val launch = getLaunchIntent(item.packageName)
        if (launch == null) {
            // Rare: no launcher entry (service-only app). Only the
            // notification's own handle can open it.
            val handled = openHandle(item, settings.bubblesEnabled, settings.allowFullScreenFallback)
            return buildResult(
                if (handled) Outcome.NOTIFICATION_HANDLE else Outcome.NONE,
                block,
                settings,
                caps,
            )
        }

        var outcome: Outcome? = null
        for (step in plan) {
            outcome = when (step) {
                Step.FLOAT ->
                    if (startFloating(launch)) {
                        Outcome.FLOATING
                    } else {
                        if (block == null) block = FloatBlockReason.REFUSED
                        null
                    }
                Step.SPLIT ->
                    if (startSplit(launch)) Outcome.SPLIT else null
                Step.FULL_SCREEN ->
                    if (startPlain(launch)) Outcome.FULL_SCREEN else null
                Step.HANDLE ->
                    if (openHandle(item, settings.bubblesEnabled, settings.allowFullScreenFallback)) {
                        Outcome.NOTIFICATION_HANDLE
                    } else {
                        null
                    }
                // Terminal last resort: every launch route failed — offer
                // the manual guided workflow instead of failing silently.
                Step.GUIDE_SMALL_WINDOW -> Outcome.SMALL_WINDOW_GUIDE
            }
            if (outcome != null) break
        }
        return buildResult(outcome ?: Outcome.NONE, block, settings, caps)
    }

    /** One place decides whether the UI may talk after a tap. */
    private fun buildResult(
        outcome: Outcome,
        block: FloatBlockReason?,
        settings: NotificationSettings,
        caps: Capabilities,
    ): LaunchResult = LaunchResult(
        outcome = outcome,
        floatBlockReason = block,
        showNotice = settings.showFloatingInstructions && when {
            outcome == Outcome.NONE || outcome == Outcome.SMALL_WINDOW_GUIDE -> true
            // Small Window phone: something opened — stay silent. A tap
            // that works is never followed by an instruction dialog.
            caps.smallWindow -> false
            else -> block != null
        },
    )

    /**
     * Last-resort guided flow — invoked only from the user's own "Open
     * app" tap in the walkthrough dialog: the source app's bubble intent
     * when bubbles are enabled (the official Android floating
     * interaction), otherwise a plain launch so the app appears in
     * Recents for the gesture.
     */
    fun openFromGuide(item: NotificationItem, settings: NotificationSettings): Boolean {
        if (settings.bubblesEnabled &&
            item.bubbleIntent != null &&
            openHandle(item, useBubbles = true, allowFullScreen = true)
        ) {
            return true
        }
        val launch = getLaunchIntent(item.packageName)
            ?: return openHandle(item, settings.bubblesEnabled, allowFullScreen = true)
        return startPlain(launch)
    }

    /**
     * Sends the source notification's own PendingIntent — the officially
     * supported Android handle. Bubble intent first when the user allows
     * bubbles; the plain content intent only when full-screen fallback is
     * allowed. CHRONOS never fabricates its own intent into another app.
     */
    fun openHandle(item: NotificationItem, useBubbles: Boolean, allowFullScreen: Boolean): Boolean {
        val target = when {
            useBubbles && item.bubbleIntent != null -> item.bubbleIntent
            allowFullScreen -> item.contentIntent
            else -> null
        } ?: return false
        return try {
            target.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            // Stale handle: retry the plain content intent once, but only
            // when full-screen launching is permitted.
            if (target !== item.contentIntent && allowFullScreen) {
                try {
                    item.contentIntent?.send()
                    item.contentIntent != null
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getLaunchIntent(packageName: String): Intent? = try {
        appContext.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * True when this device is a vivo/iQOO phone (Funtouch OS / OriginOS).
     * These always ship the native Small Window feature — the automatic
     * float attempt targets it directly, and the guided path replaces any
     * generic freeform claim so the user is never told their phone
     * "doesn't provide floating windows" when it does.
     */
    private fun hasNativeSmallWindow(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        return manufacturer == "vivo" || brand == "vivo" || brand == "iqoo"
    }

    /**
     * True when the system allows freeform windows: the PM feature flag
     * **or** the `enable_freeform_support` global — mirroring exactly how
     * `ActivityTaskManagerService.retrieveSettings()` computes
     * `mSupportsFreeformWindowManagement` (Developer options / adb toggle).
     */
    private fun supportsFreeform(): Boolean = try {
        // PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT, spelled out so
        // it compiles cleanly against every SDK level.
        appContext.packageManager.hasSystemFeature("android.software.freeform_window_management") ||
            Settings.Global.getInt(appContext.contentResolver, "enable_freeform_support", 0) != 0
    } catch (_: Exception) {
        false
    }

    /**
     * Starts [launch] as a freeform window: public `setLaunchBounds`
     * (~78 % of the screen, centred a little above the middle so CHRONOS
     * stays visible around it) plus the AOSP windowing-mode key. The key
     * is written with a plain `Bundle.putInt` — no reflection, no hidden
     * method — and the system reads it with a plain `getInt`; if freeform
     * is allowed and the target is resizable the window floats, otherwise
     * the system degrades the launch to normal and the ladder continues
     * on refusal. `FLAG_ACTIVITY_MULTIPLE_TASK` forces a fresh window:
     * without it Android reuses the app's existing full-screen task and
     * the windowing options are ignored (documented XDA/Taskbar
     * behaviour).
     */
    private fun startFloating(launch: Intent): Boolean = try {
        val dm = appContext.resources.displayMetrics
        val w = (dm.widthPixels * 0.78f).toInt()
        val h = (dm.heightPixels * 0.78f).toInt()
        val left = (dm.widthPixels - w) / 2
        val top = ((dm.heightPixels - h) * 0.36f).toInt()
        val options = ActivityOptions.makeBasic()
        options.setLaunchBounds(Rect(left, top, left + w, top + h))
        // toBundle()'s contract says callers must not modify the returned
        // Bundle — copy it first, then add the windowing-mode key.
        val bundle = Bundle()
        options.toBundle()?.let(bundle::putAll)
        bundle.putInt(KEY_LAUNCH_WINDOWING_MODE, WINDOWING_MODE_FREEFORM)
        val freeformLaunch = Intent(launch).addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        appContext.startActivity(freeformLaunch, bundle)
        true
    } catch (_: Exception) {
        false
    }

    /** Opens the app beside CHRONOS in split screen. */
    private fun startSplit(launch: Intent): Boolean = try {
        val split = Intent(launch).addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        appContext.startActivity(split)
        true
    } catch (_: Exception) {
        false
    }

    /** Opens the app with a plain full-screen launch (the normal Android way). */
    private fun startPlain(launch: Intent): Boolean = try {
        appContext.startActivity(Intent(launch))
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        /** WindowConfiguration.WINDOWING_MODE_FREEFORM (AOSP, stable). */
        private const val WINDOWING_MODE_FREEFORM = 5

        /**
         * AOSP `ActivityOptions.KEY_LAUNCH_WINDOWING_MODE` — a `@hide`
         * *string constant*, not a method: writing it into an options
         * Bundle needs no reflection and triggers no hidden-API check.
         * Verified in frameworks/base (Android 9 → 14): written by
         * `ActivityOptions.toBundle()`, read by `ActivityOptions.fromBundle`
         * with `getInt`, applied in `TaskDisplayArea.resolveWindowingMode`,
         * and **not** covered by any permission check in
         * `SafeActivityOptions.checkPermissions()`.
         */
        private const val KEY_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode"

        /**
         * Pure strategy ladder (settings × device capabilities) — kept
         * static so `FloatingAppLauncherTest` can verify the contract
         * without a Context:
         *
         *  - **Float attempt** when floating is enabled and the device can
         *    plausibly honour it: standard freeform support, or a vivo/iQOO
         *    Small Window phone (Funtouch's small window is freeform-based).
         *    Small Window phones only attempt float when the full-screen
         *    fallback is allowed too — if the system ignores the windowing
         *    hint the launch lands full-screen, and that switch must hold.
         *  - **Guided Small Window** is planned **last** — reached only
         *    when float, split, full-screen and the notification handle
         *    have all failed. A tap that can open the app never shows
         *    instructions.
         *  - full-screen fallback off → the plain full-screen step is
         *    skipped; the final handle step then only ever sends the source
         *    app's own bubble intent (official bubbles are not full-screen).
         */
        fun planFor(settings: NotificationSettings, caps: Capabilities): List<Step> {
            val attemptFloat = settings.floatingWindowsEnabled &&
                (caps.freeform || caps.smallWindow) &&
                (caps.freeform || settings.allowFullScreenFallback)
            return buildList {
                if (attemptFloat && settings.preferFloatingWindows) add(Step.FLOAT)
                add(Step.SPLIT)
                if (attemptFloat && !settings.preferFloatingWindows) add(Step.FLOAT)
                if (settings.allowFullScreenFallback) add(Step.FULL_SCREEN)
                add(Step.HANDLE)
                if (caps.smallWindow &&
                    settings.floatingWindowsEnabled &&
                    settings.showFloatingInstructions
                ) {
                    add(Step.GUIDE_SMALL_WINDOW)
                }
            }
        }
    }
}
