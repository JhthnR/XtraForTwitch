package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageStoreTest {

    private fun message(id: String) = ChatMessage(id = id, message = id)

    private fun ChatMessageStore.ids() = synchronized(messages) { messages.map { it.id } }

    @Test
    fun `only the first queued change asks for a drain`() {
        val store = ChatMessageStore()

        assertTrue(store.append(message("a")))
        assertFalse(store.append(message("b")))
        assertFalse(store.prepend(listOf(message("c"))))
        store.drain()
        assertTrue(store.append(message("d")))
    }

    @Test
    fun `queued messages are not shown until drained`() {
        val store = ChatMessageStore()
        store.append(message("a"))

        assertEquals(emptyList<String>(), store.ids())
        store.drain()
        assertEquals(listOf("a"), store.ids())
    }

    @Test
    fun `a burst of messages becomes one insert`() {
        val store = ChatMessageStore()
        store.append(message("a"))
        store.drain()
        store.append(message("b"))
        store.append(message("c"))

        val changes = store.drain()

        assertEquals(1, changes.size)
        val change = changes.single() as ChatMessageStore.Change.Appended
        assertEquals(listOf("b", "c"), change.messages.map { it.id })
        assertEquals(0, change.removedFromHead)
        assertEquals(1, change.insertedAt)
        assertEquals(listOf("a", "b", "c"), store.ids())
    }

    @Test
    fun `appending past the limit drops the oldest messages`() {
        val store = ChatMessageStore(limit = 3)
        listOf("a", "b", "c").forEach { store.append(message(it)) }
        store.drain()
        store.append(message("d"))
        store.append(message("e"))

        val change = store.drain().single() as ChatMessageStore.Change.Appended

        assertEquals(2, change.removedFromHead)
        assertEquals(1, change.insertedAt)
        assertEquals(listOf("c", "d", "e"), store.ids())
    }

    @Test
    fun `a batch larger than the limit keeps only its newest messages`() {
        val store = ChatMessageStore(limit = 2)
        store.append(message("a"))
        store.drain()
        listOf("b", "c", "d").forEach { store.append(message(it)) }

        val change = store.drain().single() as ChatMessageStore.Change.Appended

        assertEquals(listOf("c", "d"), change.messages.map { it.id })
        assertEquals(1, change.removedFromHead)
        assertEquals(0, change.insertedAt)
        assertEquals(listOf("c", "d"), store.ids())
    }

    @Test
    fun `recent messages fill only the space that is left`() {
        val store = ChatMessageStore(limit = 3)
        store.append(message("live"))
        store.drain()
        store.prepend(listOf(message("r1"), message("r2"), message("r3")))

        val change = store.drain().single() as ChatMessageStore.Change.Prepended

        assertEquals(listOf("r2", "r3"), change.messages.map { it.id })
        assertEquals(listOf("r2", "r3", "live"), store.ids())
    }

    @Test
    fun `recent messages are skipped when the list is full`() {
        val store = ChatMessageStore(limit = 1)
        store.append(message("live"))
        store.drain()
        store.prepend(listOf(message("r1")))

        assertEquals(emptyList<ChatMessageStore.Change>(), store.drain())
        assertEquals(listOf("live"), store.ids())
    }

    @Test
    fun `changes keep their order inside one drain`() {
        val store = ChatMessageStore()
        store.append(message("a"))
        store.prepend(listOf(message("r")))
        store.append(message("b"))

        val changes = store.drain()

        assertEquals(3, changes.size)
        assertEquals(0, (changes[0] as ChatMessageStore.Change.Appended).insertedAt)
        assertTrue(changes[1] is ChatMessageStore.Change.Prepended)
        assertEquals(2, (changes[2] as ChatMessageStore.Change.Appended).insertedAt)
        assertEquals(listOf("r", "a", "b"), store.ids())
    }

    @Test
    fun `clear drops shown and queued messages`() {
        val store = ChatMessageStore()
        store.append(message("a"))
        store.drain()
        store.append(message("queued"))
        store.clear()
        store.append(message("disconnected"))

        val changes = store.drain()

        assertEquals(2, changes.size)
        assertEquals(1, (changes[0] as ChatMessageStore.Change.Cleared).count)
        val appended = changes[1] as ChatMessageStore.Change.Appended
        assertEquals(0, appended.insertedAt)
        assertEquals(listOf("disconnected"), store.ids())
    }

    @Test
    fun `clearing an empty list reports nothing`() {
        val store = ChatMessageStore()
        store.clear()

        assertEquals(emptyList<ChatMessageStore.Change>(), store.drain())
    }

    @Test
    fun `find sees queued messages but not cleared ones`() {
        val store = ChatMessageStore()
        val shown = message("shown")
        store.append(shown)
        store.drain()
        val queued = message("queued")
        store.append(queued)

        assertSame(shown, store.find { it.id == "shown" })
        assertSame(queued, store.find { it.id == "queued" })

        store.clear()
        assertNull(store.find { it.id == "shown" })
        assertNull(store.find { it.id == "queued" })
    }

    @Test
    fun `the limit is at least one message`() {
        val store = ChatMessageStore(limit = 0)
        store.append(message("a"))
        store.append(message("b"))
        store.drain()

        assertEquals(listOf("b"), store.ids())
    }
}
