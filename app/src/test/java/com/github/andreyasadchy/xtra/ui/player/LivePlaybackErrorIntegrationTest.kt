package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.github.andreyasadchy.xtra.R
import com.google.common.util.concurrent.Futures
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers
import java.io.IOException
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class LivePlaybackErrorIntegrationTest {
    @Test fun `a proxied 503 bypasses recovery and executes the direct fallback once`() = Harness(true).use { h ->
        h.error = h.http(503)
        h.recovery.onPlayerError(h.error!!)
        h.render()
        verify(h.model).useCustomProxy = false
        verify(h.fragment).restartPlayer()
        assertEquals(h.context.getString(R.string.proxy_error), ShadowToast.getTextOfLatestToast())
    }

    @Test fun `a paused proxy failure never restarts playback`() = Harness(true).use { h ->
        whenever(h.controller.playWhenReady).thenReturn(false)
        h.error = h.http(503)
        h.recovery.onPlayerError(h.error!!)
        h.render()
        verify(h.fragment, never()).restartPlayer()
        verify(h.model, never()).useCustomProxy = false
        assertEquals(h.context.getString(R.string.proxy_error), ShadowToast.getTextOfLatestToast())
    }

    @Test fun `a stream ending after exhaustion still reaches the ended UI`() = Harness(false).use { h ->
        h.error = PlaybackException("network", IOException(), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        for (delay in listOf(1000L, 2500L, 5000L)) {
            h.recovery.onPlayerError(h.error!!)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(delay))
        }
        h.recovery.onPlayerError(h.error!!)
        assertTrue(h.recovery.isExhausted)
        h.error = h.http(404)
        h.recovery.onPlayerError(h.error!!)
        h.render()
        assertEquals(h.context.getString(R.string.stream_ended), ShadowToast.getTextOfLatestToast())
        verify(h.fragment, never()).restartPlayer()
    }

    @Test fun `an unretried permanent failure never promises an automatic retry`() = Harness(false).use { h ->
        h.error = h.http(403)
        h.recovery.onPlayerError(h.error!!)
        h.render()
        assertEquals(h.context.getString(R.string.player_error_stopped), ShadowToast.getTextOfLatestToast())
        verify(h.fragment, never()).restartPlayer()
    }

    private class Harness(proxy: Boolean) : AutoCloseable {
        val context = RuntimeEnvironment.getApplication()
        val fragment = spy(Media3Fragment())
        val controller = mock<MediaController>()
        val model = mock<PlayerViewModel>()
        private val servicePlayer = mock<Player>()
        var error: PlaybackException? = null
        val recovery: LivePlaybackRecovery

        init {
            val item = MediaItem.fromUri("https://example.com/live.m3u8")
            whenever(servicePlayer.currentMediaItem).thenReturn(item)
            whenever(servicePlayer.playWhenReady).thenReturn(true)
            whenever(servicePlayer.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
            whenever(servicePlayer.playbackState).thenReturn(Player.STATE_IDLE)
            whenever(servicePlayer.playerError).thenAnswer { error }
            recovery = LivePlaybackRecovery(servicePlayer, Handler(Looper.getMainLooper()), { true }, {})
            recovery.startSource(true, proxy)
            whenever(controller.applicationLooper).thenReturn(Looper.getMainLooper())
            whenever(controller.currentMediaItem).thenReturn(item)
            whenever(controller.playWhenReady).thenReturn(true)
            whenever(model.useCustomProxy).thenReturn(proxy)
            doReturn(context).whenever(fragment).context
            doReturn(View(context)).whenever(fragment).view
            doReturn(true).whenever(fragment).isAdded
            doNothing().whenever(fragment).restartPlayer()
            ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(model))
            ReflectionHelpers.setField(fragment, "controllerFuture", Futures.immediateFuture(controller))
            whenever(controller.sendCustomCommand(eq(SessionCommand(PlaybackService.GET_ERROR_CODE, Bundle.EMPTY)), any()))
                .thenAnswer {
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                        putInt(PlaybackService.RESULT, LivePlaybackRecovery.httpCode(error))
                        putBoolean(PlaybackService.RECOVERING, recovery.isErrorHandled)
                        putBoolean(PlaybackService.RECOVERY_EXHAUSTED, recovery.isExhausted)
                    }))
                }
        }

        fun http(code: Int): PlaybackException = PlaybackException("HTTP", IOException(
            HttpDataSource.InvalidResponseCodeException(code, "error", null, emptyMap(),
                DataSpec.Builder().setUri("https://example.com/live.m3u8").build(), byteArrayOf())),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)

        fun render() {
            ReflectionHelpers.callInstanceMethod<Unit>(fragment, "handleStreamError",
                ReflectionHelpers.ClassParameter.from(PlaybackException::class.java, error!!))
            shadowOf(Looper.getMainLooper()).idle()
        }
        override fun close() = recovery.dispose()
    }
}
