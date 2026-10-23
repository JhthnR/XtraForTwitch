package com.github.andreyasadchy.xtra.ui.chat

import android.app.Application
import android.os.Bundle
import com.github.andreyasadchy.xtra.util.chat.ChatReadWebSocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ChatNetworkRestorationTest {
    private fun model() = spy(ChatViewModel(RuntimeEnvironment.getApplication(),
        mock(), mock(), mock(), mock(), null, Json)).also {
        doNothing().whenever(it).startLiveChat(anyOrNull(), any())
    }

    private fun fragment(model: ChatViewModel, live: Boolean = true) = spy(ChatFragment().also {
        ReflectionHelpers.setField(it, "viewModel\$delegate", lazyOf(model))
        it.arguments = Bundle().apply {
            putBoolean("isLive", live)
            putString("channel_id", "123")
            putString("channel_login", "example")
        }
    }).also { doReturn(true).whenever(it).isResumed }

    @Test fun `network loss cancels active retry jobs and validated restoration reconnects live chat`() {
        val model = model()
        val read = Job()
        val write = Job()
        val pubSub = Job()
        val stv = Job()
        val reader = mock<ChatReadWebSocket>()
        ReflectionHelpers.setField(model, "started", true)
        ReflectionHelpers.setField(model, "chatReadJob", read)
        ReflectionHelpers.setField(model, "chatWriteJob", write)
        ReflectionHelpers.setField(model, "pubSubJob", pubSub)
        ReflectionHelpers.setField(model, "stvEventApiJob", stv)
        ReflectionHelpers.setField(model, "chatReadWebSocket", reader)
        val fragment = fragment(model)
        fragment.onNetworkLost()
        assertFalse(read.isActive)
        assertFalse(write.isActive)
        assertFalse(pubSub.isActive)
        assertFalse(stv.isActive)
        runBlocking { verify(reader, timeout(2000)).disconnect(read) }
        fragment.onNetworkRestored()
        verify(model).startLiveChat("123", "example")
    }

    @Test fun `manual disconnect and replay remain excluded while an active connection is preserved`() {
        val model = model()
        val active = Job()
        ReflectionHelpers.setField(model, "chatReadJob", active)
        val live = fragment(model)
        live.onNetworkRestored()
        verify(model, never()).startLiveChat(anyOrNull(), any())
        fragment(model, live = false).onNetworkLost()
        assertTrue("Replay must not cancel the live job", active.isActive)
        model.autoReconnect = false
        live.onNetworkLost()
        live.onNetworkRestored()
        verify(model, never()).startLiveChat(anyOrNull(), any())
        assertFalse(model.autoReconnect)
    }
}
