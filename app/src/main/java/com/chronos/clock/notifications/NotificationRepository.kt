package com.chronos.clock.notifications

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import com.chronos.clock.domain.model.NotificationGroup
import com.chronos.clock.domain.model.NotificationItem
import com.chronos.clock.domain.model.NotificationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * In-memory store of active notifications.
 *
 * Nothing here is ever persisted: content lives only in process memory and is
 * dropped when the process dies. App icons are resolved through PackageManager
 * and cached as small bitmaps keyed by package name (bounded cache).
 */
class NotificationRepository(private val appContext: Context) {

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    private val _settings = MutableStateFlow(NotificationSettings())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    private val _groups = MutableStateFlow<List<NotificationGroup>>(emptyList())
    /** Derived view: filtered + grouped notifications for the UI. */
    val groups: StateFlow<List<NotificationGroup>> = _groups.asStateFlow()

    private val iconCache = LinkedHashMap<String, android.graphics.Bitmap>(16, 0.75f, true)

    fun updateSettings(settings: NotificationSettings) {
        _settings.value = settings
        recompute()
    }

    /**
     * What the user last dismissed, by notification key. Chat apps often
     * re-post the *identical* notification right after a dismiss or a quick
     * reply (sync echo / read receipt). Without this memory the icon popped
     * straight back in, which looked like "closing notifications does
     * nothing". Content-identical re-posts are ignored for
     * [DISMISS_MEMORY_MS]; a genuinely new message has new text/time and
     * always shows up normally.
     */
    private data class DismissMark(
        val content: String,
        val at: Long,
        /** Set after a quick reply: suppress *any* re-post until then. */
        val quietUntil: Long = 0L,
    )

    /** Access-ordered bounded map: least recently touched entry evicted first. */
    private val dismissed = LinkedHashMap<String, DismissMark>(16, 0.75f, true)

    fun onPosted(item: NotificationItem) {
        synchronized(dismissed) {
            val mark = dismissed[item.key]
            if (mark != null) {
                val now = SystemClock.elapsedRealtime()
                val sameContent = mark.content == item.contentSignature()
                val quiet = now < mark.quietUntil
                if (quiet || (sameContent && now - mark.at <= DISMISS_MEMORY_MS)) {
                    // The user already dismissed exactly this content —
                    // keep it out of the row (banner silencing in the
                    // listener keeps it out of the shade as well).
                    return
                }
                dismissed.remove(item.key)
            }
        }
        _items.update { NotificationReducer.onPosted(it, item) }
        recompute()
    }

    fun onRemoved(key: String) {
        val item = _items.value.firstOrNull { it.key == key }
        if (item != null) {
            synchronized(dismissed) {
                dismissed[key] = DismissMark(
                    content = item.contentSignature(),
                    at = SystemClock.elapsedRealtime(),
                )
                while (dismissed.size > DISMISS_MEMORY_MAX) {
                    val eldest = dismissed.entries.iterator()
                    if (!eldest.hasNext()) break
                    eldest.next()
                    eldest.remove()
                }
            }
        }
        _items.update { NotificationReducer.onRemoved(it, key) }
        recompute()
    }

    /**
     * After a quick reply the source app rewrites its notification almost
     * immediately (read receipt, "sent" echo). Suppress every re-post of
     * that key for [durationMs] so the icon cannot pop straight back in.
     */
    fun suppressFor(key: String, durationMs: Long) {
        synchronized(dismissed) {
            val mark = dismissed[key] ?: return
            dismissed[key] = mark.copy(quietUntil = SystemClock.elapsedRealtime() + durationMs)
        }
    }

    fun clear() {
        synchronized(dismissed) { dismissed.clear() }
        _items.value = emptyList()
        recompute()
    }

    /** Stable identity of what the notification *says* (not when it posted). */
    private fun NotificationItem.contentSignature(): String = buildString {
        append(title).append('\u0000')
        append(text).append('\u0000')
        append(styleMessages.size)
        for (m in styleMessages) {
            append('\u0000').append(m.sender).append('\u0001').append(m.text)
                .append('\u0001').append(m.time)
        }
    }

    private fun recompute() {
        val s = _settings.value
        val filtered = NotificationReducer.applyFilter(_items.value, s)
        _groups.value = if (s.integrationEnabled && s.showAppIcons) {
            NotificationReducer.group(filtered)
        } else {
            emptyList()
        }
    }

    /**
     * Loads the *original* application icon for [packageName].
     *
     * CHRONOS always shows the source app's own icon — it never recolours or
     * replaces another app's notification icon (Android does not allow that).
     * Icons are cached; failures return null so the UI can fall back to a
     * neutral placeholder.
     */
    suspend fun loadIcon(packageName: String): android.graphics.Bitmap? =
        withContext(Dispatchers.IO) {
            synchronized(iconCache) { iconCache[packageName] }?.let { return@withContext it }
            val drawable: Drawable = try {
                appContext.packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                return@withContext null
            }
            // Bound decode: application icons are already small; cap to 192px.
            val size = minOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 192)
                .coerceAtLeast(48)
            val bitmap = drawable.toBitmap(width = size, height = size)
            synchronized(iconCache) {
                if (iconCache.size >= 48) {
                    iconCache.entries.iterator().let { it.next(); it.remove() }
                }
                iconCache[packageName] = bitmap
            }
            bitmap
        }

    /** True when the system reports CHRONOS as the enabled notification listener. */
    fun hasAccess(): Boolean =
        com.chronos.clock.core.permissions.PermissionHelper.hasNotificationAccess(appContext)

    /** Expected listener component, for debugging / settings deep link. */
    fun listenerComponent(): ComponentName =
        ComponentName(appContext, NotificationListener::class.java)

    private companion object {
        /** How long an identical re-post of a dismissed notification is ignored. */
        const val DISMISS_MEMORY_MS = 120_000L

        /** Maximum dismissed keys remembered (LRU). */
        const val DISMISS_MEMORY_MAX = 64
    }
}
