package com.github.andreyasadchy.xtra.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRowImageBinderTest {

    private val frame = mutableListOf<Runnable>()
    private val binder = ChatRowImageBinder(
        postOnFrame = { frame.add(it) },
        removeFromFrame = { frame.remove(it) },
    )

    private fun runFrame() {
        val callbacks = frame.toList()
        frame.clear()
        callbacks.forEach { it.run() }
    }

    @Test
    fun `images loaded during the bind need only the row's own bind`() {
        var binds = 0

        binder.bind({ binds++ }) { onImageLoaded ->
            repeat(5) { onImageLoaded() }
        }

        assertEquals(1, binds)
        assertTrue(frame.isEmpty())
    }

    @Test
    fun `images loaded later rebind once per frame`() {
        var binds = 0
        lateinit var onImageLoaded: () -> Unit
        binder.bind({ binds++ }) { onImageLoaded = it }

        repeat(3) { onImageLoaded() }
        assertEquals(1, frame.size)
        runFrame()
        assertEquals(2, binds)

        onImageLoaded()
        runFrame()
        assertEquals(3, binds)
    }

    @Test
    fun `images for a replaced bind never reach the row`() {
        var oldBinds = 0
        var newBinds = 0
        lateinit var oldImageLoaded: () -> Unit
        binder.bind({ oldBinds++ }) { oldImageLoaded = it }
        oldImageLoaded()

        binder.bind({ newBinds++ }) { }
        oldImageLoaded()
        runFrame()

        assertEquals(1, oldBinds)
        assertEquals(1, newBinds)
        assertTrue(frame.isEmpty())
    }

    @Test
    fun `images for a recycled row are ignored`() {
        var binds = 0
        lateinit var onImageLoaded: () -> Unit
        binder.bind({ binds++ }) { onImageLoaded = it }
        onImageLoaded()

        binder.cancel()
        onImageLoaded()
        runFrame()

        assertEquals(1, binds)
    }
}
