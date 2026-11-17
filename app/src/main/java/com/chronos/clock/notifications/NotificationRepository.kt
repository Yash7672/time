package com.chronos.clock.notifications

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
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

    fun onPosted(item: NotificationItem) = _items.update { NotificationReducer.onPosted(it, item) }
        .also { recompute() }

    fun onRemoved(key: String) = _items.update { NotificationReducer.onRemoved(it, key) }
        .also { recompute() }

    fun clear() {
        _items.value = emptyList()
        recompute()
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
}
