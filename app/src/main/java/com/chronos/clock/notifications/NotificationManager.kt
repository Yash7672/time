package com.chronos.clock.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import com.chronos.clock.domain.model.NotificationItem

/**
 * Facade the UI uses for notification interactions. All operations are
 * best-effort and swallow Android's expected failure modes (cancelled
 * PendingIntents, revoked listener access) so a click can never crash CHRONOS.
 */
class NotificationManager(
    private val appContext: Context,
    /** Shared in-memory store — the listener writes into this same instance. */
    private val repository: NotificationRepository,
) {

    val items get() = repository.items
    val groups get() = repository.groups
    val settings get() = repository.settings

    fun onPosted(item: NotificationItem) = repository.onPosted(item)

    fun onRemoved(key: String) = repository.onRemoved(key)

    fun clear() = repository.clear()

    suspend fun loadIcon(packageName: String) = repository.loadIcon(packageName)

    fun hasAccess(): Boolean = repository.hasAccess()

    /**
     * Opens the source app's conversation. Preference order:
     *  1. bubble intent (when the user enabled bubbles and the app provides one),
     *  2. the notification's own content PendingIntent.
     * Both are Android-provided handles — CHRONOS never pokes another app's UI.
     */
    fun open(item: NotificationItem, useBubbles: Boolean): Boolean {
        val target: PendingIntent? = when {
            useBubbles && item.bubbleIntent != null -> item.bubbleIntent
            else -> item.contentIntent
        }
        if (target == null) return false
        return try {
            target.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            // Stale handle; fall back to the plain content intent once.
            if (target !== item.contentIntent) {
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

    /**
     * Opens the source app on top of CHRONOS.
     *
     * Preference order:
     *  1. **Floating window** (freeform) when the device supports it — the
     *     app appears as a small window floating over CHRONOS.
     *  2. **Split screen** beside CHRONOS ([Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT]).
     *  3. A plain full-screen launch.
     * Back inside the opened window closes it and CHRONOS takes the screen
     * back. Every step degrades to the next — never a crash.
     */
    fun openInMultiWindow(item: NotificationItem): Boolean {
        val launch = try {
            appContext.packageManager.getLaunchIntentForPackage(item.packageName)
        } catch (_: Exception) {
            null
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // 1) Floating window over CHRONOS when the device has freeform.
            if (supportsFreeform() && startFloating(launch)) return true

            // 2) Split screen beside CHRONOS.
            launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            return try {
                appContext.startActivity(launch)
                true
            } catch (_: Exception) {
                // Split refused by the system: retry once as a plain launch.
                try {
                    launch.flags = launch.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT.inv()
                    appContext.startActivity(launch)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        // No launcher entry (rare): fall back to the notification's own
        // handle — bubble intent first when the user prefers bubbles.
        return open(item, useBubbles = repository.settings.value.bubblesEnabled)
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
     * Starts [launch] as a floating window covering ~78 % of the screen,
     * centred a little above the middle so CHRONOS stays visible around it.
     * Returns false when the system refuses the bounds, so the caller can
     * fall back to split screen.
     */
    private fun startFloating(launch: Intent): Boolean = try {
        val dm = appContext.resources.displayMetrics
        val w = (dm.widthPixels * 0.78f).toInt()
        val h = (dm.heightPixels * 0.78f).toInt()
        val left = (dm.widthPixels - w) / 2
        val top = ((dm.heightPixels - h) * 0.36f).toInt()
        val options = ActivityOptions.makeBasic()
            .setLaunchBounds(Rect(left, top, left + w, top + h))
        appContext.startActivity(launch, options.toBundle())
        true
    } catch (_: Exception) {
        false
    }

    /** Dismiss through the listener service; only possible with granted access. */
    fun dismiss(item: NotificationItem): Boolean {
        val listener = NotificationListener.active
        return if (listener != null) {
            listener.dismiss(item)
            true
        } else {
            // Service not bound right now: drop it locally at least.
            repository.onRemoved(item.key)
            false
        }
    }

    fun reply(item: NotificationItem, text: String, actionIndex: Int = 0): Boolean =
        QuickReplyHandler.send(appContext, item, actionIndex, text)
}