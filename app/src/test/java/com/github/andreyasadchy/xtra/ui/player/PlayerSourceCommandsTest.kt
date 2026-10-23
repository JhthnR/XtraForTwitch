package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.SettableFuture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerSourceCommandsTest {
    private val controller = mock<MediaController>()
    private val command = SessionCommand(PlaybackService.START_OFFLINE_VIDEO, Bundle.EMPTY)
    private val result = SettableFuture.create<SessionResult>()
    private val rejected = mutableListOf<Pair<String, Int>>()
    private var current: MediaController? = controller
    private val commands = PlayerSourceCommands({ it === current }, { action, code -> rejected.add(action to code) })

    init { whenever(controller.sendCustomCommand(command, Bundle.EMPTY)).thenReturn(result) }
    private fun drain() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun `a rejected current download reports failure without changing receiver playback`() {
        commands.send(controller, command)
        result.set(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        drain()
        assertEquals(listOf(PlaybackService.START_OFFLINE_VIDEO to SessionError.ERROR_NOT_SUPPORTED), rejected)
        verify(controller, never()).pause()
        verify(controller, never()).stop()
        verify(controller, never()).clearMediaItems()
    }

    @Test fun `accepted source does not invoke rejection`() {
        commands.send(controller, command)
        result.set(SessionResult(SessionResult.RESULT_SUCCESS))
        drain()
        assertTrue(rejected.isEmpty())
    }

    @Test fun `an old queued rejection cannot discard a newer start`() {
        commands.send(controller, command)
        result.set(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        val newer = SettableFuture.create<SessionResult>()
        whenever(controller.sendCustomCommand(command, Bundle.EMPTY)).thenReturn(newer)
        commands.send(controller, command)
        newer.set(SessionResult(SessionResult.RESULT_SUCCESS))
        drain()
        assertTrue(rejected.isEmpty())
    }

    @Test fun `stopped view cancels pending result and queued failure`() {
        commands.send(controller, command)
        commands.cancel()
        assertTrue(result.isCancelled)
        drain()
        assertTrue(rejected.isEmpty())
    }

    @Test fun `changed controller rejects no replacement UI`() {
        commands.send(controller, command)
        current = mock()
        result.set(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        drain()
        assertTrue(rejected.isEmpty())
    }

    @Test fun `failed command future reports a generic start failure`() {
        commands.send(controller, command)
        result.setException(IllegalStateException("Disconnected"))
        drain()
        assertEquals(listOf(PlaybackService.START_OFFLINE_VIDEO to SessionError.ERROR_UNKNOWN), rejected)
    }
}
