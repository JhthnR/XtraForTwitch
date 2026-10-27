package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class SourceStartPositionTest {
    private val context = RuntimeEnvironment.getApplication()

    /** Mirrors PlaybackService's MediaSession player wrapper. */
    private class SessionPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {
        override fun getState(): State {
            val state = super.getState()
            return state.buildUpon().setAvailableCommands(state.availableCommands.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM).build()).build()
        }
    }

    private val item = MediaItem.Builder().setUri("https://example.com/video.mp4").setMimeType("video/mp4").build()

    /**
     * Cast off routes through RoutePlayer; Cast on adds a forwarding layer in CastPlayer's place.
     * Both stay local. CastPlaybackHandoffTest covers the receiver call.
     */
    private fun withSession(block: (PlayerCastBridge, Player, () -> ExoPlayer) -> Unit) {
        for (cast in listOf(false, true)) {
            lateinit var local: ExoPlayer
            val bridge = PlayerCastBridge(context, cast, { ExoPlayer.Builder(context).build().also { local = it } },
                endCurrentSession = {}, createCast = { player, _ -> object : ForwardingSimpleBasePlayer(player) {} })
            val session = bridge.guardCommands(SessionPlayer(bridge.player))
            try { block(bridge, session) { local } } finally { bridge.dispose(); session.release() }
        }
    }

    @Test fun `resumed VoD starts at its saved position before the playlist is seekable`() = withSession { bridge, session, local ->
        assertTrue(bridge.handoff.setSource(bridge.player, ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)), item, 600_000))
        session.prepare()
        session.playWhenReady = true
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(600_000L, local().currentPosition)
    }

    @Test fun `download resume and quality swaps keep position through the session player`() = withSession { _, session, local ->
        session.setMediaItem(item, 600_000)
        session.prepare()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(600_000L, local().currentPosition)
    }

    @Test fun `new sources without a saved position start from the beginning`() = withSession { bridge, session, local ->
        assertTrue(bridge.handoff.setSource(bridge.player, ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)), item))
        session.prepare()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0L, local().currentPosition)
    }
}
