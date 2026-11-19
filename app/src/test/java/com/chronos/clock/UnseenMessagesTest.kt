package com.chronos.clock

import com.chronos.clock.domain.model.NotificationGroup
import com.chronos.clock.domain.model.NotificationItem
import com.chronos.clock.domain.model.StyleMessage
import com.chronos.clock.presentation.home.buildUnseenMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reply-sheet contract: the "last unseen messages" list contains **every**
 * message of **every** active notification of the app — MessagingStyle
 * notifications expanded into their individual messages, plain ones as
 * title/text previews — in chronological reading order, nothing capped.
 */
class UnseenMessagesTest {

    private fun item(
        key: String,
        postTime: Long,
        title: String? = "Alice",
        text: String? = "hello",
        style: List<StyleMessage> = emptyList(),
    ) = NotificationItem(
        key = key,
        packageName = "com.example.chat",
        appLabel = "Chat",
        id = key.hashCode(),
        tag = null,
        postTime = postTime,
        title = title,
        text = text,
        styleMessages = style,
    )

    private fun group(vararg items: NotificationItem) = NotificationGroup(
        packageName = "com.example.chat",
        appLabel = "Chat",
        count = items.size,
        latest = items.maxByOrNull { it.postTime }!!,
        items = items.sortedByDescending { it.postTime },
    )

    @Test
    fun `messaging style notification expands into every message`() {
        val style = listOf(
            StyleMessage(sender = "Alice", text = "hey", time = 1_000),
            StyleMessage(sender = "Alice", text = "you there?", time = 2_000),
            StyleMessage(sender = null, text = "yes", time = 3_000),
        )
        val rows = buildUnseenMessages(
            group(item("n1", postTime = 50, style = style)),
        )

        assertEquals(3, rows.size)
        assertEquals("hey", rows[0].text)
        assertEquals("you there?", rows[1].text)
        assertEquals("yes", rows[2].text)
        assertEquals("Alice", rows[1].sender)
        assertNull(rows[2].sender)
        assertEquals(2_000L, rows[1].time)
    }

    @Test
    fun `style messages replace the notification preview`() {
        // One notification, several messages — and no duplicate summary row.
        val rows = buildUnseenMessages(
            group(
                item(
                    "n1",
                    postTime = 50,
                    title = "Chat",
                    text = "hey",
                    style = listOf(StyleMessage("Alice", "hey", 1_000)),
                ),
            ),
        )
        assertEquals(1, rows.size)
        assertEquals("hey", rows[0].text)
    }

    @Test
    fun `plain notification contributes title and text as one row`() {
        val rows = buildUnseenMessages(
            group(item("n1", postTime = 10, title = "Bob", text = "hi")),
        )
        assertEquals(1, rows.size)
        assertEquals("Bob", rows[0].sender)
        assertEquals("hi", rows[0].text)
        assertEquals(10L, rows[0].time)
    }

    @Test
    fun `title-only notification shows the title as content`() {
        val rows = buildUnseenMessages(
            group(item("n1", postTime = 7, title = "3 new messages", text = null)),
        )
        assertEquals(1, rows.size)
        assertNull(rows[0].sender)
        assertEquals("3 new messages", rows[0].text)
    }

    @Test
    fun `oldest notification first so the list reads chronologically`() {
        val rows = buildUnseenMessages(
            group(
                item("new", postTime = 300, title = "A", text = "third"),
                item("old", postTime = 100, title = "A", text = "first"),
                item("mid", postTime = 200, title = "A", text = "second"),
            ),
        )
        assertEquals(listOf("first", "second", "third"), rows.map { it.text })
    }

    @Test
    fun `nothing readable contributes no row`() {
        val rows = buildUnseenMessages(
            group(item("n1", postTime = 1, title = null, text = null)),
        )
        assertEquals(0, rows.size)
    }
}
