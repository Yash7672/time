package com.chronos.clock

import com.chronos.clock.core.constants.AppConstants
import com.chronos.clock.domain.model.NotificationItem
import com.chronos.clock.domain.model.NotificationPrivacy
import com.chronos.clock.domain.model.NotificationSettings
import com.chronos.clock.notifications.NotificationReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReducerTest {

    private fun item(
        key: String,
        pkg: String = "com.example.app",
        postTime: Long = 0L,
        summary: Boolean = false,
        group: String? = "g1",
        title: String? = "Title",
    ) = NotificationItem(
        key = key,
        packageName = pkg,
        appLabel = pkg,
        id = key.hashCode(),
        tag = null,
        postTime = postTime,
        title = title,
        text = null,
        isGroupSummary = summary,
        groupKey = group,
    )

    @Test
    fun `same key never duplicates`() {
        var list = NotificationReducer.onPosted(emptyList(), item("a", postTime = 1))
        list = NotificationReducer.onPosted(list, item("a", postTime = 2, title = "Updated"))

        assertEquals(1, list.size)
        assertEquals("Updated", list[0].title)
        assertEquals(2, list[0].postTime)
    }

    @Test
    fun `removal deletes exactly one entry`() {
        var list = NotificationReducer.onPosted(emptyList(), item("a"))
        list = NotificationReducer.onPosted(list, item("b"))
        assertEquals(2, list.size)

        list = NotificationReducer.onRemoved(list, "a")
        assertEquals(1, list.size)
        assertEquals("b", list[0].key)

        // Removing an unknown key is a no-op.
        list = NotificationReducer.onRemoved(list, "zzz")
        assertEquals(1, list.size)
    }

    @Test
    fun `group summary collapses into its children`() {
        var list = NotificationReducer.onPosted(emptyList(), item("sum", summary = true, group = "g1"))
        assertEquals(1, list.size)

        list = NotificationReducer.onPosted(list, item("child1", group = "g1", postTime = 10))
        list = NotificationReducer.onPosted(list, item("child2", group = "g1", postTime = 11))

        // Summary removed once children exist; children both kept.
        assertEquals(2, list.size)
        assertTrue(list.none { it.isGroupSummary })
    }

    @Test
    fun `list is bounded to the maximum`() {
        var list: List<NotificationItem> = emptyList()
        val total = AppConstants.MAX_NOTIFICATIONS + 25
        for (i in 0 until total) {
            list = NotificationReducer.onPosted(list, item("k$i", postTime = i.toLong()))
        }
        assertEquals(AppConstants.MAX_NOTIFICATIONS, list.size)
        // Newest entries survive: list is newest-first, oldest are dropped.
        assertEquals(total - 1, list.first().postTime.toInt())
        assertEquals(total - AppConstants.MAX_NOTIFICATIONS, list.last().postTime.toInt())
    }

    @Test
    fun `allow-list filters packages and empty means all`() {
        val items = listOf(
            item("a", pkg = "com.whatsapp"),
            item("b", pkg = "com.telegram"),
        )

        val all = NotificationSettings(allowedPackages = emptySet())
        assertEquals(2, NotificationReducer.applyFilter(items, all).size)

        val only = NotificationSettings(allowedPackages = setOf("com.whatsapp"))
        val filtered = NotificationReducer.applyFilter(items, only)
        assertEquals(1, filtered.size)
        assertEquals("com.whatsapp", filtered[0].packageName)
    }

    @Test
    fun `grouping aggregates counts per app sorted by recency`() {
        val items = listOf(
            item("w1", pkg = "com.whatsapp", postTime = 100),
            item("w2", pkg = "com.whatsapp", postTime = 300),
            item("t1", pkg = "com.telegram", postTime = 200),
            item("sum", pkg = "com.whatsapp", postTime = 999, summary = true),
        )

        val groups = NotificationReducer.group(items)
        assertEquals(2, groups.size)
        // WhatsApp has 2 real notifications (summary excluded).
        val wa = groups.first { it.packageName == "com.whatsapp" }
        assertEquals(2, wa.count)
        assertEquals(300L, wa.latest.postTime)
        // Sorted by recency: whatsapp (300) before telegram (200).
        assertEquals("com.whatsapp", groups[0].packageName)
    }

    @Test
    fun `default privacy hides titles`() {
        assertEquals(NotificationPrivacy.ICONS_ONLY, NotificationSettings().privacy)
    }

    @Test
    fun `star animation collapses per amoled mode`() {
        val stars = com.chronos.clock.domain.model.StarSettings(enabled = true)
        assertFalse(
            stars.effectivelyAnimated(com.chronos.clock.domain.model.AmoledMode.ULTRA_BATTERY),
        )
        assertFalse(
            stars.effectivelyAnimated(com.chronos.clock.domain.model.AmoledMode.PURE_AMOLED),
        )
        assertTrue(
            stars.effectivelyAnimated(com.chronos.clock.domain.model.AmoledMode.SPACE_AMOLED),
        )
        assertFalse(
            stars.copy(enabled = false)
                .effectivelyAnimated(com.chronos.clock.domain.model.AmoledMode.SPACE_AMOLED),
        )
    }
}
