package com.github.andreyasadchy.xtra.ui.player

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal open class PlaybackSessionCallback : MediaSession.Callback {
    override fun onConnectAsync(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.ConnectionResult> {
        // The old onConnect default is a placeholder in Media3 1.11. Build the
        // real controller defaults so surface, tracks and playback stay available.
        val defaults = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
        if (!controller.isTrusted) return Futures.immediateFuture(defaults)
        val commands = defaults.availableSessionCommands.buildUpon().apply {
            add(SessionCommand(PlaybackService.START_STREAM, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.START_VIDEO, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.START_CLIP, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.START_OFFLINE_VIDEO, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.END_CAST_SESSION, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.CLOSE_SOURCE, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.GET_SOURCE_REQUEST, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.GET_CAST_AVAILABLE, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.TOGGLE_DYNAMICS_PROCESSING, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.TOGGLE_PROXY, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.SET_SLEEP_TIMER, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.CHECK_ADS, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.GET_QUALITIES, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.GET_DURATION, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.GET_ERROR_CODE, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.GET_MEDIA_PLAYLIST, Bundle.EMPTY))
            add(SessionCommand(PlaybackService.GET_MULTIVARIANT_PLAYLIST, Bundle.EMPTY))
        }.build()
        return Futures.immediateFuture(MediaSession.ConnectionResult.accept(commands, defaults.availablePlayerCommands))
    }
}
