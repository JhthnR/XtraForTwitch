package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class CastStopRestartTest {
    private val endSession = SessionCommand(PlaybackService.END_CAST_SESSION, Bundle.EMPTY)
    private val remote = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
    private val local = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).build()
    private val controller = mock<MediaController>()
    private val result = SettableFuture.create<SessionResult>()
    private val listeners = mutableListOf<Player.Listener>()
    private val stopRestart = CastStopRestart(timeoutMs = 1_000L)
    private var restarts = 0
    private var failures = 0

    init {
        whenever(controller.deviceInfo).thenReturn(remote)
        whenever(controller.sendCustomCommand(endSession, Bundle.EMPTY)).thenReturn(result)
        doAnswer { listeners.add(it.getArgument(0)) }.whenever(controller).addListener(any())
        doAnswer { listeners.remove(it.getArgument<Player.Listener>(0)) }.whenever(controller).removeListener(any())
    }

    private fun start() = stopRestart.start(controller, { restarts++ }, { failures++ })
    private fun drain() = shadowOf(Looper.getMainLooper()).idle()
    private fun becomeLocal() {
        whenever(controller.deviceInfo).thenReturn(local)
        listeners.toList().forEach { it.onDeviceInfoChanged(local) }
    }

    @Test fun `clears receiver media before ending the session`() {
        start()
        inOrder(controller) {
            verify(controller).stop()
            verify(controller).clearMediaItems()
            verify(controller).sendCustomCommand(endSession, Bundle.EMPTY)
        }
    }

    @Test fun `restarts once when playback becomes local`() {
        start()
        assertTrue(stopRestart.isPending)
        becomeLocal()
        assertFalse(stopRestart.isPending)
        result.set(SessionResult(SessionResult.RESULT_SUCCESS))
        drain()
        assertEquals(1, restarts)
        assertEquals(0, failures)
        assertTrue(listeners.isEmpty())
    }

    @Test fun `restarts when playback is already local as the command completes`() {
        start()
        whenever(controller.deviceInfo).thenReturn(local)
        result.set(SessionResult(SessionResult.RESULT_SUCCESS))
        drain()
        assertEquals(1, restarts)
    }

    @Test fun `does not restart while the receiver is still active`() {
        start()
        result.set(SessionResult(SessionResult.RESULT_SUCCESS))
        drain()
        assertEquals(0, restarts)
        assertEquals(0, failures)
    }

    @Test fun `fails after the timeout and ignores a late local switch`() {
        start()
        shadowOf(Looper.getMainLooper()).idleFor(1_000L, TimeUnit.MILLISECONDS)
        assertEquals(1, failures)
        becomeLocal()
        assertEquals(0, restarts)
    }

    @Test fun `cancel drops the pending restart`() {
        start()
        stopRestart.cancel()
        becomeLocal()
        result.set(SessionResult(SessionResult.RESULT_SUCCESS))
        shadowOf(Looper.getMainLooper()).idleFor(1_000L, TimeUnit.MILLISECONDS)
        assertEquals(0, restarts)
        assertEquals(0, failures)
        assertTrue(listeners.isEmpty())
    }
}
