package com.chronos.clock.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import com.chronos.clock.domain.model.NotificationItem
import com.chronos.clock.domain.model.NotificationSettings

/**
 * Opens other apps on top of CHRONOS using **public Android APIs only** —
 * no reflection, no hidden windowing APIs, no accessibility gestures, no
 * fake windows, no overlay permission.
 *
 * Capability model (each device class gets its own honest path):
 *
 *  - **vivo / iQOO (Funtouch OS, OriginOS)** — the native **Small Window**
 *    feature is the real floating window on these phones (rounded corners,
 *    title bar with minimize/maximize/close, draggable, resizable, Mini
 *    Small Window mode). vivo publishes no public intent or API that a
 *    third-party app can use to launch Small Window directly — their
 *    developer documentation is an *app adaptation* guide, not a launch
 *    API. So instead of pretending, [planFor] returns
 *    [Step.GUIDE_SMALL_WINDOW]: the UI shows the exact Recents workflow
 *    (Settings → Shortcuts → Small window docs) and only opens the app
 *    when the user taps "Open app" themselves ([openFromGuide]).
 *  - **Stock freeform devices** — advertise
 *    `FEATURE_FREEFORM_WINDOW_MANAGEMENT`; the public
 *    `ActivityOptions.setLaunchBounds` attempt runs there. Whether the
 *    OS honours freeform is the OS's decision — CHRONOS never claims a
 *    floating success it cannot verify.
 *  - **Everything else** — split screen → full-screen → the source
 *    notification's own handle, each step degrading to the next.
 *
 * The strategy ladder is a pure function of settings × capabilities
 * ([planFor]), unit-tested in `FloatingAppLauncherTest`.
 */
class FloatingAppLauncher(private val appContext: Context) {

    /** Which strategy actually opened the app (or what the tap produced). */
    enum class Outcome {
        /** Opened with freeform window bounds (device advertises freeform). */
        FLOATING,
        /** Opened beside CHRONOS in split screen. */
        SPLIT,
        /** Opened with a plain full-screen launch. */
        FULL_SCREEN,
        /** Opened through the source notification's own PendingIntent. */
        NOTIFICATION_HANDLE,
        /** Nothing launched: the guided Small Window walkthrough was shown. */
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
        /** `PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT` present. */
        val freeform: Boolean,
        /** vivo/iQOO device shipping the native Small Window feature. */
        val smallWindow: Boolean,
    )

    /** Result of one icon tap. */
    data class LaunchResult(
        val outcome: Outcome,
        /** Non-null when floating was preferred but could not be attempted. */
        val floatBlockReason: FloatBlockReason? = null,
    ) {
        val launched: Boolean get() = outcome != Outcome.NONE
    }

    /**
     * Opens [item]'s app on top of CHRONOS following [planFor]. CHRONOS
     * itself is never closed: launches go through the application context
     * with FLAG_ACTIVITY_NEW_TASK, so the clock keeps running behind the
     * new window and closing that window returns to CHRONOS untouched.
     */
    fun open(item: NotificationItem, settings: NotificationSettings): LaunchResult {
        val caps = Capabilities(
            freeform = supportsFreeform(),
            smallWindow = hasNativeSmallWindow(),
        )
        val plan = planFor(settings, caps)

        // Guided Small Window flow: terminal — nothing is launched here.
        // The user's own "Open app" tap in the dialog calls openFromGuide,
        // so a full-screen open never happens silently.
        if (Step.GUIDE_SMALL_WINDOW in plan) {
            return LaunchResult(Outcome.SMALL_WINDOW_GUIDE)
        }

        // Honest block tracking: only when floating was explicitly PREFERRED
        // and the device has neither standard freeform nor the guided
        // native path — then (and only then) the UI explains it.
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
            return if (openHandle(item, settings.bubblesEnabled, settings.allowFullScreenFallback)) {
                LaunchResult(Outcome.NOTIFICATION_HANDLE, block)
            } else {
                LaunchResult(Outcome.NONE, block)
            }
        }

        for (step in plan) {
            when (step) {
                Step.FLOAT ->
                    if (startFloating(launch)) {
                        return LaunchResult(Outcome.FLOATING, block)
                    } else if (block == null) {
                        block = FloatBlockReason.REFUSED
                    }
                Step.SPLIT ->
                    if (startSplit(launch)) return LaunchResult(Outcome.SPLIT, block)
                Step.FULL_SCREEN ->
                    if (startPlain(launch)) return LaunchResult(Outcome.FULL_SCREEN, block)
                Step.HANDLE ->
                    if (openHandle(item, settings.bubblesEnabled, settings.allowFullScreenFallback)) {
                        return LaunchResult(Outcome.NOTIFICATION_HANDLE, block)
                    }
                Step.GUIDE_SMALL_WINDOW ->
                    Unit // terminal; handled before the loop
            }
        }
        return LaunchResult(Outcome.NONE, block)
    }

    /**
     * Step 1 of the guided Small Window flow — invoked only from the user's
     * own "Open app" tap: the source app's bubble intent when bubbles are
     * enabled (the official Android floating interaction), otherwise a
     * plain launch so the app appears in Recents for the gesture.
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
     * These always ship the native Small Window feature — the guided path
     * replaces any generic freeform claim so the user is never told their
     * phone "doesn't provide floating windows" when it does.
     */
    private fun hasNativeSmallWindow(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        return manufacturer == "vivo" || brand == "vivo" || brand == "iqoo"
    }

    /** True when the device advertises standard Android freeform windows. */
    private fun supportsFreeform(): Boolean = try {
        // PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT, spelled out so
        // it compiles cleanly against every SDK level.
        appContext.packageManager.hasSystemFeature("android.software.freeform_window_management")
    } catch (_: Exception) {
        false
    }

    /**
     * Starts [launch] with public window bounds (~78 % of the screen,
     * centred a little above the middle so CHRONOS stays visible around
     * it). `ActivityOptions.setLaunchBounds` is the documented public hint;
     * whether the OS places the activity in freeform is the OS's decision
     * (this step is only planned on devices that advertise freeform).
     * Any refusal returns false so the ladder continues.
     */
    private fun startFloating(launch: Intent): Boolean = try {
        val dm = appContext.resources.displayMetrics
        val w = (dm.widthPixels * 0.78f).toInt()
        val h = (dm.heightPixels * 0.78f).toInt()
        val left = (dm.widthPixels - w) / 2
        val top = ((dm.heightPixels - h) * 0.36f).toInt()
        val options = ActivityOptions.makeBasic()
        options.setLaunchBounds(Rect(left, top, left + w, top + h))
        appContext.startActivity(Intent(launch), options.toBundle())
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
        /**
         * Pure strategy ladder (settings × device capabilities) — kept
         * static so `FloatingAppLauncherTest` can verify the contract
         * without a Context:
         *
         *  - **Small Window device** (vivo/iQOO) + floating enabled +
         *    instructions on + full-screen allowed → `[GUIDE_SMALL_WINDOW]`
         *    alone: the guided Recents workflow, nothing launched yet.
         *  - freeform device → float (if preferred) → split → full-screen →
         *    handle, honouring every Floating App Windows switch.
         *  - otherwise → split → full-screen → handle (no float step is
         *    planned where it cannot work, so no float claim can be made).
         *  - full-screen fallback off → the plain full-screen step is
         *    skipped; the final handle step then only ever sends the source
         *    app's own bubble intent (official bubbles are not full-screen).
         */
        fun planFor(settings: NotificationSettings, caps: Capabilities): List<Step> {
            if (caps.smallWindow &&
                settings.floatingWindowsEnabled &&
                settings.showFloatingInstructions &&
                settings.allowFullScreenFallback
            ) {
                return listOf(Step.GUIDE_SMALL_WINDOW)
            }
            val canFloat = settings.floatingWindowsEnabled && caps.freeform && !caps.smallWindow
            return buildList {
                if (canFloat && settings.preferFloatingWindows) add(Step.FLOAT)
                add(Step.SPLIT)
                if (canFloat && !settings.preferFloatingWindows) add(Step.FLOAT)
                if (settings.allowFullScreenFallback) {
                    add(Step.FULL_SCREEN)
                    add(Step.HANDLE)
                } else {
                    add(Step.HANDLE)
                }
            }
        }
    }
}
