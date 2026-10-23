package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.model.chat.ChatMessage

/**
 * Owns the message list that [ChatAdapter] shows.
 *
 * Chat sockets and replay call [append], [prepend] and [clear] from any thread. Those calls
 * only queue the change; [drain] applies everything queued on the main thread, so the list
 * never changes between an adapter notification and the layout that reads it, and a burst
 * of messages becomes one insert instead of one notification per message.
 */
class ChatMessageStore(limit: Int = DEFAULT_LIMIT) {

    sealed interface Change {
        /** [removedFromHead] messages were dropped from the start, then [messages] were added at [insertedAt]. */
        class Appended(val messages: List<ChatMessage>, val removedFromHead: Int, val insertedAt: Int) : Change

        /** [messages] were added at the start. */
        class Prepended(val messages: List<ChatMessage>) : Change

        /** All [count] messages were removed. */
        class Cleared(val count: Int) : Change
    }

    private sealed interface Operation {
        class Append(val message: ChatMessage) : Operation
        class Prepend(val messages: List<ChatMessage>) : Operation
        object Clear : Operation
    }

    /** Changed only by [drain] on the main thread. Other threads must hold its lock to read it. */
    val messages = mutableListOf<ChatMessage>()

    @Volatile
    var limit = limit.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
        }

    private val pending = ArrayList<Operation>()

    /** Queues [message]. Returns true when the caller has to schedule a [drain]. */
    fun append(message: ChatMessage) = enqueue(Operation.Append(message))

    /** Queues older [messages] for the start of the list. Returns true when the caller has to schedule a [drain]. */
    fun prepend(messages: List<ChatMessage>) = enqueue(Operation.Prepend(messages))

    /** Queues removing every message, including queued ones. Returns true when the caller has to schedule a [drain]. */
    fun clear() = synchronized(pending) {
        val wasEmpty = pending.isEmpty()
        pending.clear()
        pending.add(Operation.Clear)
        wasEmpty
    }

    private fun enqueue(operation: Operation) = synchronized(pending) {
        val wasEmpty = pending.isEmpty()
        pending.add(operation)
        wasEmpty
    }

    /** Finds a message that is shown or still queued. Safe to call from any thread. */
    fun find(predicate: (ChatMessage) -> Boolean): ChatMessage? {
        synchronized(messages) {
            synchronized(pending) {
                for (operation in pending.asReversed()) {
                    when (operation) {
                        is Operation.Append -> if (predicate(operation.message)) return operation.message
                        is Operation.Prepend -> operation.messages.lastOrNull(predicate)?.let { return it }
                        Operation.Clear -> return null
                    }
                }
            }
            return messages.lastOrNull(predicate)
        }
    }

    /** Applies every queued change in order. Call on the main thread, then notify the adapter with the result. */
    fun drain(): List<Change> = synchronized(messages) {
        val operations = synchronized(pending) {
            pending.toList().also { pending.clear() }
        }
        val changes = ArrayList<Change>()
        val appended = ArrayList<ChatMessage>()
        fun applyAppended() {
            if (appended.isNotEmpty()) {
                changes.add(applyAppend(appended))
                appended.clear()
            }
        }
        for (operation in operations) {
            when (operation) {
                is Operation.Append -> appended.add(operation.message)
                is Operation.Prepend -> {
                    applyAppended()
                    applyPrepend(operation.messages)?.let { changes.add(it) }
                }
                Operation.Clear -> {
                    appended.clear()
                    if (messages.isNotEmpty()) {
                        changes.add(Change.Cleared(messages.size))
                        messages.clear()
                    }
                }
            }
        }
        applyAppended()
        changes
    }

    private fun applyAppend(batch: List<ChatMessage>): Change.Appended {
        // Messages that would be evicted in the same batch are never shown.
        val added = batch.takeLast(limit)
        val removed = (messages.size + added.size - limit).coerceIn(0, messages.size)
        if (removed > 0) {
            messages.subList(0, removed).clear()
        }
        val insertedAt = messages.size
        messages.addAll(added)
        return Change.Appended(added, removed, insertedAt)
    }

    private fun applyPrepend(batch: List<ChatMessage>): Change.Prepended? {
        val left = limit - messages.size
        if (left <= 0 || batch.isEmpty()) {
            return null
        }
        val added = batch.takeLast(left)
        messages.addAll(0, added)
        return Change.Prepended(added)
    }

    companion object {
        const val DEFAULT_LIMIT = 600
    }
}
