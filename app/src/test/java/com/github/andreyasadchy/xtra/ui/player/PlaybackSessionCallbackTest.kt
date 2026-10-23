package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class PlaybackSessionCallbackTest {
    private fun connect(trusted: Boolean): MediaSession.ConnectionResult {
        val controller = mock<MediaSession.ControllerInfo>()
        whenever(controller.isTrusted).thenReturn(trusted)
        return PlaybackSessionCallback().onConnectAsync(mock(), controller).get(1, TimeUnit.SECONDS)
    }

    @Test fun `app and trusted controllers can attach video read tracks and control playback`() {
        val result = connect(trusted = true)
        assertTrue(result.isAccepted)
        for (command in listOf(Player.COMMAND_SET_VIDEO_SURFACE, Player.COMMAND_GET_TRACKS,
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            assertTrue("Missing player command $command", result.availablePlayerCommands.contains(command))
        }
        for (command in listOf(PlaybackService.START_VIDEO, PlaybackService.START_STREAM, PlaybackService.GET_QUALITIES, PlaybackService.END_CAST_SESSION)) {
            assertTrue(result.availableSessionCommands.contains(SessionCommand(command, Bundle.EMPTY)))
        }
    }

    @Test fun `untrusted controllers retain Media3 read permissions without playback mutations`() {
        val result = connect(trusted = false)
        assertTrue(result.isAccepted)
        assertEquals(MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_PLAYER_COMMANDS, result.availablePlayerCommands)
        assertFalse(result.availablePlayerCommands.contains(Player.COMMAND_PLAY_PAUSE))
        assertFalse(result.availableSessionCommands.contains(SessionCommand(PlaybackService.START_STREAM, Bundle.EMPTY)))
        assertFalse(result.availableSessionCommands.contains(SessionCommand(PlaybackService.END_CAST_SESSION, Bundle.EMPTY)))
    }
}
