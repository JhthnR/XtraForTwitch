package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
class BackgroundPlaybackRetryTest {
    @Test fun `transient background failure re-prepares after a delay`() {
        val h = Harness()
        h.fail()
        h.advance(1_999)
        verify(h.player, never()).prepare()
        h.advance(1)
        verify(h.player).prepare()
    }

    @Test fun `persistent failure stops after five delayed attempts`() {
        val h = Harness()
        for (delay in listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)) {
            h.fail()
            h.advance(delay)
        }
        h.fail()
        h.advance(600_000)
        verify(h.player, times(5)).prepare()
    }

    @Test fun `non-transient errors are never retried`() {
        val h = Harness()
        h.fail(PlaybackException.ERROR_CODE_DECODING_FAILED)
        h.fail(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        h.advance(600_000)
        verify(h.player, never()).prepare()
    }

    @Test fun `ineligible playback is never retried`() {
        val h = Harness()
        h.fail(eligible = false)
        h.advance(600_000)
        verify(h.player, never()).prepare()
    }

    @Test fun `repeated errors before the retry runs schedule only one prepare`() {
        val h = Harness()
        h.fail()
        h.fail()
        h.fail()
        h.advance(2_000)
        verify(h.player, times(1)).prepare()
    }

    @Test fun `a new source or user action cancels the pending retry`() {
        val h = Harness()
        h.fail()
        h.item = MediaItem.fromUri("https://example.com/other.m3u8")
        h.advance(2_000)
        h.fail()
        h.state = Player.STATE_BUFFERING
        h.advance(5_000)
        h.fail()
        h.retry.reset()
        h.advance(600_000)
        verify(h.player, never()).prepare()
    }

    @Test fun `thirty seconds of playback restores the budget`() {
        val h = Harness()
        for (delay in listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)) {
            h.fail()
            h.advance(delay)
        }
        h.retry.onIsPlayingChanged(true)
        h.advance(29_999)
        h.fail()
        h.advance(600_000)
        verify(h.player, times(5)).prepare()
        h.retry.onIsPlayingChanged(true)
        h.advance(30_000)
        h.fail()
        h.advance(2_000)
        verify(h.player, times(6)).prepare()
    }

    @Test fun `playlist change resets the budget`() {
        val h = Harness()
        for (delay in listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)) {
            h.fail()
            h.advance(delay)
        }
        h.retry.onMediaItemTransition(h.item, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        h.fail()
        h.advance(2_000)
        verify(h.player, times(6)).prepare()
    }

    private class Harness {
        val player = mock<Player>()
        var item: MediaItem? = MediaItem.fromUri("https://example.com/vod.m3u8")
        var state = Player.STATE_IDLE
        var error: PlaybackException? = null
        val retry: BackgroundPlaybackRetry

        init {
            whenever(player.currentMediaItem).thenAnswer { item }
            whenever(player.playbackState).thenAnswer { state }
            whenever(player.playerError).thenAnswer { error }
            retry = BackgroundPlaybackRetry(player, Handler(Looper.getMainLooper()))
        }

        fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        fun fail(code: Int = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, eligible: Boolean = true) {
            state = Player.STATE_IDLE
            error = PlaybackException("network", IOException(), code)
            retry.onError(error!!, eligible)
        }
    }
}
