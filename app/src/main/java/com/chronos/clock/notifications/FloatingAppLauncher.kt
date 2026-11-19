package com.chronos.clock.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import com.chronos.clock.domain.model.NotificationItem
import com.chronos.clock.domain.model.NotificationSettings

/**
 * Dedicated launcher that opens other apps on top of CHRONOS using
 * Android's OWN windowing — a real freeform (floating) activity window,
 * split screen, or a normal launch. It never draws a fake window around
 * an icon, never overlays other apps (CHRONOS has no overlay permission),
 * never embeds or controls another app's UI, and never uses accessibility
 * services.
 *
 * Honest capability model: Android does not let an ordinary third-party
 * app force freeform on every device. Availability depends on the OEM
 * build (`FEATURE_FREEFORM_WINDOW_MANAGEMENT`) and on the target app's
 * resize declaration — both are checked before every attempt, every step
 * degrades to the next, and the caller always learns which strategy won
 * ([LaunchResult.outcome]) and why floating could not engage, if it could
 * not ([LaunchResult.floatBlockReason]).
 *
 * The strategy ladder itself is decided by [plan] — a pure function of the
 * settings, unit-tested in `FloatingAppLauncherTest`.
 */
class FloatingAppLauncher(private val appContext: Context) {

    /** Which strategy actually opened the app. */
    enum class Outcome {
        /** Opened as a real freeform window floating over CHRONOS. */
        FLOATING,
        /** Opened beside CHRONOS in split screen. */
        SPLIT,
        /** Opened with a plain full-screen launch. */
        FULL_SCREEN,
        /** Opened through the source notification's own PendingIntent. */
        NOTIFICATION_HANDLE,
        /** Nothing was launched at all. */
        NONE,
    }

    /** Why a floating window could not be used (drives the user-facing notice). */
    enum class FloatBlockReason {
        /** The device does not advertise freeform-window support. */
        DEVICE_UNSUPPORTED,
        /** The target app declares itself non-resizable — Android refuses to float it. */
        TARGET_UNRESIZABLE,
        /** The system refused the freeform attempt itself. */
        REFUSED,
    }

    /** One step of the launch ladder. */
    enum class Step { FLOAT, SPLIT, FULL_SCREEN, HANDLE }

    /** Result of one icon tap. */
    data class LaunchResult(
        val outcome: Outcome,
        /** Non-null when floating was wanted but could not engage. */
        val floatBlockReason: FloatBlockReason? = null,
    ) {
        val launched: Boolean get() = outcome != Outcome.NONE
    }

    /**
     * Strategy ladder for the given settings — the pure [planFor] function
     * of the settings, so `FloatingAppLauncherTest` can verify the
     * settings → behaviour contract without a Context:
     *
     *  - floating on + prefer on  → float → split → full-screen → handle
     *  - floating on + prefer off → split → float → full-screen → handle
     *  - floating off             → split → full-screen → handle
     *  - full-screen fallback off → the plain full-screen step is skipped;
     *    the final handle step then only ever sends the source app's own
     *    bubble intent (official bubbles are not a full-screen launch).
     */
    fun plan(settings: NotificationSettings): List<Step> = planFor(settings)

    /**
     * Opens [item]'s app on top of CHRONOS following [plan]. CHRONOS itself
     * is never closed: every launch goes through the application context with
     * FLAG_ACTIVITY_NEW_TASK, so the clock keeps running behind the new
     * window, and closing that window returns to CHRONOS untouched.
     */
    fun open(item: NotificationItem, settings: NotificationSettings): LaunchResult {
        val launch = try {
            appContext.packageManager.getLaunchIntentForPackage(item.packageName)
        } catch (_: Exception) {
            null
        }

        if (launch == null) {
            // Rare: no launcher entry (service-only app). Only the
            // notification's own handle can open it.
            return if (openHandle(item, settings.bubblesEnabled, settings.allowFullScreenFallback)) {
                LaunchResult(Outcome.NOTIFICATION_HANDLE)
            } else {
                LaunchResult(Outcome.NONE)
            }
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Capability probe — both are decided by the OS and the target app,
        // never by CHRONOS.
        val deviceFreeform = supportsFreeform()
        val resizable = targetAllowsResize(launch)
        var block: FloatBlockReason? = null

        for (step in plan(settings)) {
            when (step) {
                Step.FLOAT -> when {
                    !deviceFreeform ->
                        if (block == null) block = FloatBlockReason.DEVICE_UNSUPPORTED
                    !resizable ->
                        if (block == null) block = FloatBlockReason.TARGET_UNRESIZABLE
                    startFloating(launch) ->
                        return LaunchResult(Outcome.FLOATING)
                    else ->
                        if (block == null) block = FloatBlockReason.REFUSED
                }
                Step.SPLIT ->
                    if (startSplit(launch)) return LaunchResult(Outcome.SPLIT, block)
                Step.FULL_SCREEN ->
                    if (startPlain(launch)) return LaunchResult(Outcome.FULL_SCREEN, block)
                Step.HANDLE ->
                    if (openHandle(item, settings.bubblesEnabled, settings.allowFullScreenFallback)) {
                        return LaunchResult(Outcome.NOTIFICATION_HANDLE, block)
                    }
            }
        }
        return LaunchResult(Outcome.NONE, block)
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

    /** True when the device advertises freeform (floating) windows. */
    private fun supportsFreeform(): Boolean = try {
        // PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT, spelled out so
        // it compiles cleanly against every SDK level.
        appContext.packageManager.hasSystemFeature("android.software.freeform_window_management")
    } catch (_: Exception) {
        false
    }

    /**
     * True when the target activity allows itself to be resized — apps that
     * declare `resizeableActivity=false` are refused by Android in both
     * freeform and (older) split mode, so trying to float them would only
     * produce a plain full-screen launch.
     */
    private fun targetAllowsResize(launch: Intent): Boolean = try {
        val component = launch.component ?: return true
        val info = appContext.packageManager.getActivityInfo(component, 0)
        // ActivityInfo.resizeMode is hidden from the SDK: read it by
        // reflection. RESIZE_MODE_UNRESIZEABLE == 0 (also the default for
        // pre-N apps) means the app forbids resizing; anything else may be
        // floated. If the field cannot be read, assume resizable — the
        // attempt then degrades exactly like the plain bounds launch.
        val mode = runCatching {
            android.content.pm.ActivityInfo::class.java
                .getField("resizeMode")
                .getInt(info)
        }.getOrDefault(2)
        mode != 0
    } catch (_: Exception) {
        true
    }

    /**
     * Starts [launch] as a floating window covering ~78 % of the screen,
     * centred a little above the middle so CHRONOS stays visible around it.
     *
     * Sets the FREEFORM windowing mode explicitly (hidden API — reached by
     * reflection, best-effort) and supplies bounds; together these are what
     * the window manager needs to place the app on top of CHRONOS. Bounds
     * alone never guarantee freeform — that is why the windowing mode is set
     * explicitly and any refusal returns false for the next step.
     */
    private fun startFloating(launch: Intent): Boolean = try {
        val dm = appContext.resources.displayMetrics
        val w = (dm.widthPixels * 0.78f).toInt()
        val h = (dm.heightPixels * 0.78f).toInt()
        val left = (dm.widthPixels - w) / 2
        val top = ((dm.heightPixels - h) * 0.36f).toInt()
        val options = ActivityOptions.makeBasic()
        // ActivityOptions.setLaunchWindowingMode(5 = WINDOWING_MODE_FREEFORM)
        // is hidden from the public SDK; reflection is wrapped so a blocked
        // call just falls back to bounds-only launching.
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Integer.TYPE)
                .invoke(options, 5)
        }
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
         * Pure strategy ladder — kept static so `FloatingAppLauncherTest`
         * can verify the settings → behaviour contract without a Context.
         */
        fun planFor(settings: NotificationSettings): List<Step> = buildList {
            val tryFloat = settings.floatingWindowsEnabled
            if (tryFloat && settings.preferFloatingWindows) add(Step.FLOAT)
            add(Step.SPLIT)
            if (tryFloat && !settings.preferFloatingWindows) add(Step.FLOAT)
            if (settings.allowFullScreenFallback) {
                add(Step.FULL_SCREEN)
                add(Step.HANDLE)
            } else {
                add(Step.HANDLE)
            }
        }
    }
}
