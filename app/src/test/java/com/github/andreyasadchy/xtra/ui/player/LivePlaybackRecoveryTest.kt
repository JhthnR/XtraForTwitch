package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.LooperMode
import java.io.IOException
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class LivePlaybackRecoveryTest {
    @Test fun `transient failure uses delayed prepare without replacing source or user choices`() {
        val h = Harness()
        h.fail()
        assertTrue(h.recovery.isRecovering)
        h.advance(999)
        verify(h.player, never()).prepare()
        h.advance(1)
        verify(h.player).prepare()
        verify(h.player, never()).clearMediaItems()
        verify(h.player, never()).setMediaItem(any())
        verify(h.player, never()).setPlayWhenReady(any())
        verify(h.player, never()).setPlaybackSpeed(any())
        verify(h.player, never()).seekToDefaultPosition()
    }

    @Test fun `three retries exhaust once and short healthy flaps cannot reset budget`() {
        val h = Harness()
        for (delay in listOf(1000L, 2500L, 5000L)) {
            h.fail()
            h.advance(delay)
            h.ready()
            h.advance(29_000)
        }
        h.fail()
        h.advance(120_000)
        h.fail()
        verify(h.player, times(3)).prepare()
        assertEquals(1, h.exhaustions)
        assertFalse(h.recovery.isErrorHandled)
        assertTrue(h.recovery.isExhausted)
    }

    @Test fun `thirty seconds of continuous playback grants a fresh retry budget`() {
        val h = Harness()
        h.fail()
        h.advance(1000)
        h.ready()
        h.advance(30_000)
        h.fail()
        h.advance(1000)
        verify(h.player, times(2)).prepare()
        assertEquals(0, h.exhaustions)
    }

    @Test fun `offline wait is bounded without spending attempts or spinning prepares`() {
        val h = Harness()
        h.network = false
        h.fail()
        h.advance(59_999)
        verify(h.player, never()).prepare()
        assertEquals(0, h.exhaustions)
        h.advance(1)
        assertEquals(1, h.exhaustions)
        h.network = true
        h.advance(120_000)
        verify(h.player, never()).prepare()
    }

    @Test fun `network return resumes the pending attempt while preserving pause intent`() {
        val h = Harness()
        h.network = false
        h.fail()
        h.advance(2000)
        h.network = true
        h.advance(1000)
        verify(h.player).prepare()
        h.fail()
        h.wantsPlay = false
        h.events()
        h.advance(120_000)
        verify(h.player, times(1)).prepare()
        assertFalse(h.recovery.isRecovering)
    }

    @Test fun `close and source replacement invalidate pending work`() {
        for (replacement in listOf<MediaItem?>(null, MediaItem.fromUri("https://example.com/new.m3u8"))) {
            val h = Harness()
            h.fail()
            h.item = replacement
            h.events()
            h.advance(120_000)
            verify(h.player, never()).prepare()
            h.recovery.dispose()
        }
    }

    @Test fun `stop without an error cancels recovery`() {
        val h = Harness()
        h.fail()
        h.error = null
        h.events()
        h.advance(120_000)
        verify(h.player, never()).prepare()
    }

    @Test fun `remote route and disposal cancel timers and never restart either player`() {
        val h = Harness()
        h.fail()
        h.remote = true
        h.events()
        h.advance(120_000)
        verify(h.player, never()).prepare()
        h.remote = false
        h.fail()
        h.recovery.dispose()
        h.advance(120_000)
        verify(h.player, never()).prepare()
        verify(h.player).removeListener(h.listener)
    }

    @Test fun `non-live source has no automatic live recovery`() {
        val h = Harness()
        h.fail()
        h.recovery.startSource(false)
        h.fail()
        h.advance(120_000)
        verify(h.player, never()).prepare()
    }

    @Test fun `behind-live-window seeks to live before preparing`() {
        val h = Harness()
        h.fail(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)
        h.advance(1000)
        inOrder(h.player) {
            verify(h.player).seekToDefaultPosition()
            verify(h.player).prepare()
        }
    }

    @Test fun `buffering watchdog stops then prepares with a finite retry budget`() {
        val h = Harness()
        for (delay in listOf(1000L, 2500L, 5000L)) {
            h.state = Player.STATE_BUFFERING
            h.events()
            h.advance(15_000 + delay)
        }
        h.events()
        h.advance(15_000)
        verify(h.player, times(3)).stop()
        verify(h.player, times(3)).prepare()
        verify(h.player, never()).clearMediaItems()
        assertEquals(1, h.exhaustions)
    }

    @Test fun `permanent errors do not retry and nested HTTP status is classified narrowly`() {
        for (code in listOf(401, 403, 404, 410)) assertFalse(LivePlaybackRecovery.isTransient(httpError(code)))
        for (code in listOf(408, 429, 500, 503, 599)) assertTrue(LivePlaybackRecovery.isTransient(httpError(code)))
        val h = Harness()
        h.error = httpError(404)
        h.listener.onPlayerError(h.error!!)
        h.events()
        h.advance(120_000)
        verify(h.player, never()).prepare()
        assertEquals(404, LivePlaybackRecovery.httpCode(h.error))
        assertFalse(LivePlaybackRecovery.isTransient(PlaybackException("decoder", null, PlaybackException.ERROR_CODE_DECODING_FAILED)))
    }

    @Test fun `custom proxy HTTP failures stay available for the direct fallback instead of retrying the proxy`() {
        for (code in listOf(408, 429, 500, 503, 599)) {
            val h = Harness()
            h.recovery.startSource(true, customProxy = true)
            h.error = httpError(code)
            h.listener.onPlayerError(h.error!!)
            h.events()
            h.advance(120_000)
            assertFalse(h.recovery.isErrorHandled)
            assertFalse(h.recovery.isExhausted)
            assertEquals(code, LivePlaybackRecovery.httpCode(h.error))
            verify(h.player, never()).prepare()
            h.recovery.dispose()
        }
    }

    @Test fun `exhaustion does not hide a later permanent error and explicit prepare restores the retry budget`() {
        val h = Harness()
        h.exhaust()
        h.error = httpError(404)
        h.listener.onPlayerError(h.error!!)
        assertFalse(h.recovery.isErrorHandled)
        assertEquals(404, LivePlaybackRecovery.httpCode(h.error))
        h.recovery.onExplicitPrepare()
        assertFalse(h.recovery.isExhausted)
        h.fail()
        h.advance(1000)
        verify(h.player, times(4)).prepare()
    }

    @Test fun `routine controller prepare during buffering or ready cannot reset failed attempts`() {
        val h = Harness()
        for (delay in listOf(1000L, 2500L, 5000L)) {
            h.fail()
            h.advance(delay)
            h.error = null
            h.state = Player.STATE_BUFFERING
            h.recovery.onExplicitPrepare()
            h.ready()
            h.recovery.onExplicitPrepare()
            h.advance(29_000)
        }
        h.fail()
        assertTrue(h.recovery.isExhausted)
        verify(h.player, times(3)).prepare()
    }

    @Test fun `metadata refresh keeps a pending attempt and does not restart the healthy timer`() {
        val h = Harness()
        h.fail()
        h.item = h.item!!.buildUpon().setMediaMetadata(MediaMetadata.Builder().setTitle("updated title").build()).build()
        h.events()
        h.advance(1000)
        verify(h.player).prepare()
        h.ready()
        h.advance(20_000)
        h.item = h.item!!.buildUpon().setMediaMetadata(MediaMetadata.Builder().setTitle("another update").build()).build()
        h.events()
        h.advance(10_000)
        h.fail()
        h.advance(1000)
        verify(h.player, times(2)).prepare()
        assertFalse(h.recovery.isExhausted)
    }

    @Test fun `a long offline wait gets one finite network-return episode and network flaps cannot rearm it`() {
        val h = Harness()
        h.network = false
        h.recovery.onConnectivityChanged(false)
        h.fail()
        h.advance(60_000)
        assertTrue(h.recovery.isExhausted)
        h.network = true
        h.recovery.onConnectivityChanged(true)
        h.recovery.onConnectivityChanged(true)
        h.advance(1000)
        verify(h.player).seekToDefaultPosition()
        verify(h.player).prepare()
        h.fail(); h.advance(2500)
        h.fail(); h.advance(5000)
        h.fail()
        assertTrue(h.recovery.isExhausted)
        h.recovery.onConnectivityChanged(false)
        h.recovery.onConnectivityChanged(true)
        h.advance(120_000)
        verify(h.player, times(3)).prepare()
        assertEquals(2, h.exhaustions)
    }

    @Test fun `network return cannot revive paused remote replaced disposed or permanently failed sources`() {
        for (condition in listOf("paused", "remote", "replaced", "disposed", "permanent")) {
            val h = Harness()
            h.network = false
            h.recovery.onConnectivityChanged(false)
            h.fail()
            h.advance(60_000)
            when (condition) {
                "paused" -> h.wantsPlay = false
                "remote" -> h.remote = true
                "replaced" -> { h.item = MediaItem.fromUri("https://example.com/new.m3u8"); h.events() }
                "disposed" -> h.recovery.dispose()
                "permanent" -> { h.error = httpError(404); h.listener.onPlayerError(h.error!!) }
            }
            h.network = true
            h.recovery.onConnectivityChanged(true)
            h.advance(120_000)
            verify(h.player, never()).prepare()
            h.recovery.dispose()
        }
    }

    private fun httpError(code: Int): PlaybackException {
        val cause = HttpDataSource.InvalidResponseCodeException(code, "error", null, emptyMap(),
            DataSpec.Builder().setUri("https://example.com/live.m3u8").build(), byteArrayOf())
        return PlaybackException("HTTP", IOException(cause), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
    }

    private class Harness {
        val player = mock<Player>()
        var item: MediaItem? = MediaItem.fromUri("https://example.com/live.m3u8")
        var state = Player.STATE_IDLE
        var error: PlaybackException? = null
        var wantsPlay = true
        var playing = false
        var network = true
        var remote = false
        var exhaustions = 0
        val recovery: LivePlaybackRecovery
        val listener: Player.Listener

        init {
            whenever(player.currentMediaItem).thenAnswer { item }
            whenever(player.playbackState).thenAnswer { state }
            whenever(player.playerError).thenAnswer { error }
            whenever(player.playWhenReady).thenAnswer { wantsPlay }
            whenever(player.isPlaying).thenAnswer { playing }
            whenever(player.deviceInfo).thenAnswer {
                DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build()
            }
            recovery = LivePlaybackRecovery(player, Handler(Looper.getMainLooper()), { network }, { exhaustions++ })
            listener = argumentCaptor<Player.Listener>().run { verify(player).addListener(capture()); firstValue }
            recovery.startSource(true)
            events()
        }

        fun events() = listener.onEvents(player, mock())
        fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        fun fail(code: Int = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
            playing = false
            state = Player.STATE_IDLE
            error = PlaybackException("network", IOException(), code)
            listener.onPlayerError(error!!)
            events()
        }
        fun ready() {
            error = null
            state = Player.STATE_READY
            playing = true
            events()
        }
        fun exhaust() {
            for (delay in listOf(1000L, 2500L, 5000L)) { fail(); advance(delay) }
            fail()
        }
    }
}
