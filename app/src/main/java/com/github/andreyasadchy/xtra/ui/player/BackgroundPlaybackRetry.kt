package com.github.andreyasadchy.xtra.ui.player

import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Re-prepares a failed non-live source while the app plays in the background.
 * Only transient network errors are retried, a few times with growing delays;
 * thirty seconds of continuous playback grants a fresh budget.
 */
internal class BackgroundPlaybackRetry(
    private val player: Player,
    private val handler: Handler,
) : Player.Listener {
    private var attempts = 0
    private var retry: Runnable? = null
    private var healthy: Runnable? = null

    /** Called for every player error; [eligible] is false for live, remote and foreground playback. */
    fun onError(error: PlaybackException, eligible: Boolean) {
        cancelHealthy()
        if (!eligible || retry != null || !LivePlaybackRecovery.isTransient(error) || attempts >= DELAYS.size) return
        val item = player.currentMediaItem ?: return
        val task = object : Runnable {
            override fun run() {
                if (retry !== this) return
                retry = null
                if (player.currentMediaItem === item && player.playbackState == Player.STATE_IDLE && player.playerError != null) {
                    player.prepare()
                }
            }
        }
        retry = task
        handler.postDelayed(task, DELAYS[attempts++])
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        cancelHealthy()
        if (isPlaying && attempts > 0) {
            healthy = Runnable { healthy = null; attempts = 0 }.also { handler.postDelayed(it, HEALTHY_MS) }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) reset()
    }

    /** Drops any pending retry and restores the full budget, e.g. for a new source or foreground playback. */
    fun reset() {
        cancelHealthy()
        retry?.let(handler::removeCallbacks)
        retry = null
        attempts = 0
    }

    private fun cancelHealthy() {
        healthy?.let(handler::removeCallbacks)
        healthy = null
    }

    companion object {
        private val DELAYS = longArrayOf(2_000, 5_000, 10_000, 20_000, 30_000)
        private const val HEALTHY_MS = 30_000L
    }
}
