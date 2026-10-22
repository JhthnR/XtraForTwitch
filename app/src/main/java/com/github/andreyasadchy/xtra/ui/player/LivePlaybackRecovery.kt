package com.github.andreyasadchy.xtra.ui.player

import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.github.andreyasadchy.xtra.BuildConfig

/** One main-looper recovery owner for the current local live source. Never starts playback. */
@OptIn(UnstableApi::class)
internal class LivePlaybackRecovery(
    private val player: Player,
    private val handler: Handler,
    private val networkAvailable: () -> Boolean,
    private val exhausted: () -> Unit,
) : Player.Listener {
    private var live = false
    private var disposed = false
    private var owner: MediaItem? = null
    private var attempts = 0
    private var retry: Runnable? = null
    private var healthy: Runnable? = null
    private var stall: Runnable? = null
    private var notified = false
    private var proxied = false
    private var offlineObserved = false
    private var networkResumeUsed = false
    private var needsLiveEdge = false
    val isLiveSource get() = live
    val isRecovering get() = retry != null
    val isErrorHandled get() = isRecovering
    val isExhausted get() = notified

    init { player.addListener(this) }

    /** Called before an explicit source command, including switching away from live. */
    fun startSource(isLive: Boolean, customProxy: Boolean = false) {
        cancelCallbacks()
        live = isLive
        owner = null
        attempts = 0
        notified = false
        proxied = customProxy
        offlineObserved = !networkAvailable()
        needsLiveEdge = offlineObserved
        networkResumeUsed = false
    }

    /** A controller Prepare is explicit intent; automatic attempts bypass this hook. */
    fun onExplicitPrepare() {
        if (disposed || !live || player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return
        cancelCallbacks()
        attempts = 0
        notified = false
        networkResumeUsed = false
        if (needsLiveEdge && player.currentMediaItem != null) player.seekToDefaultPosition()
        needsLiveEdge = false
        trace("explicit-prepare")
    }

    /** One additional finite episode after an observed outage, never on READY flaps. */
    fun onConnectivityChanged(available: Boolean) {
        if (disposed) return
        val returned = offlineObserved && available
        offlineObserved = !available
        if (!available) needsLiveEdge = true
        if (!returned || !notified || networkResumeUsed || !eligible() || owner != player.currentMediaItem ||
            player.playbackState == Player.STATE_ENDED || player.playerError?.let {
                !isTransient(it) || (proxied && httpCode(it) >= 400)
            } == true) return
        networkResumeUsed = true
        attempts = 0
        notified = false
        trace("network-return")
        schedule(true)
    }

    override fun onPlayerError(error: PlaybackException) {
        cancelHealthy()
        cancelStall()
        trace("error", error)
        if (eligible() && isTransient(error) && !(proxied && httpCode(error) >= 400)) {
            schedule(error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)
        }
        else cancelRetry()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (owner != player.currentMediaItem) {
            cancelCallbacks()
            owner = player.currentMediaItem
            attempts = 0
            notified = false
            networkResumeUsed = false
        }
        if (!eligible()) {
            cancelCallbacks()
            return
        }
        if ((player.playbackState == Player.STATE_IDLE && player.playerError == null) || player.playbackState == Player.STATE_ENDED) {
            cancelCallbacks()
            return
        }
        if (player.isPlaying) {
            cancelRetry()
            cancelStall()
            if (healthy == null) {
                healthy = Runnable {
                    healthy = null
                    if (eligible() && player.isPlaying) {
                        attempts = 0
                        notified = false
                        networkResumeUsed = false
                    }
                }.also { handler.postDelayed(it, HEALTHY_MS) }
            }
        } else {
            cancelHealthy()
            if (player.playbackState == Player.STATE_BUFFERING && retry == null && stall == null && !notified) {
                val item = player.currentMediaItem
                stall = Runnable {
                    stall = null
                    if (eligible() && player.currentMediaItem == item && player.playbackState == Player.STATE_BUFFERING) schedule(false)
                }.also { handler.postDelayed(it, STALL_MS) }
            }
        }
    }

    private fun eligible() = !disposed && live && player.playWhenReady && player.currentMediaItem != null &&
        player.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE

    private fun schedule(liveEdge: Boolean) {
        if (retry != null || notified) return
        if (attempts >= DELAYS.size) return giveUp()
        // Error callbacks precede the batched events; establish ownership now.
        owner = player.currentMediaItem
        val item = owner
        val deadline = SystemClock.uptimeMillis() + NETWORK_WAIT_MS
        val task = object : Runnable {
            override fun run() {
                if (retry !== this) return
                if (!eligible() || player.currentMediaItem != item) {
                    cancelRetry()
                    return
                }
                if (!networkAvailable()) {
                    offlineObserved = true
                    needsLiveEdge = true
                    if (SystemClock.uptimeMillis() >= deadline) giveUp()
                    else handler.postDelayed(this, NETWORK_POLL_MS)
                    return
                }
                retry = null
                attempts++
                // Retain the configured source, selected tracks, volume and playWhenReady.
                if (liveEdge || needsLiveEdge) player.seekToDefaultPosition()
                needsLiveEdge = false
                if (player.playbackState == Player.STATE_BUFFERING) player.stop()
                trace("attempt")
                player.prepare()
            }
        }
        retry = task
        handler.postDelayed(task, DELAYS[attempts])
    }

    private fun giveUp() {
        cancelCallbacks()
        if (!notified && eligible()) {
            notified = true
            trace("exhausted")
            exhausted()
        }
    }

    fun dispose() {
        disposed = true
        cancelCallbacks()
        player.removeListener(this)
    }

    private fun cancelRetry() { retry?.let(handler::removeCallbacks); retry = null }
    private fun cancelHealthy() { healthy?.let(handler::removeCallbacks); healthy = null }
    private fun cancelStall() { stall?.let(handler::removeCallbacks); stall = null }
    private fun cancelCallbacks() { cancelRetry(); cancelHealthy(); cancelStall() }

    private fun trace(event: String, error: PlaybackException? = player.playerError) {
        if (!BuildConfig.DEBUG) return
        // Deliberately exclude URLs, headers, tokens and account/channel metadata.
        Log.d("LiveRecovery", "event=$event attempt=$attempts state=${player.playbackState} " +
            "play=${player.playWhenReady} playing=${player.isPlaying} error=${error?.errorCode} " +
            "http=${httpCode(error)} position=${player.currentPosition} offset=${player.currentLiveOffset} " +
            "duration=${player.duration} online=${networkAvailable()} exhausted=$notified")
    }

    companion object {
        private val DELAYS = longArrayOf(1_000, 2_500, 5_000)
        private const val HEALTHY_MS = 30_000L
        private const val STALL_MS = 15_000L
        private const val NETWORK_WAIT_MS = 60_000L
        private const val NETWORK_POLL_MS = 1_000L

        fun httpCode(error: PlaybackException?): Int = generateSequence(error?.cause) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode ?: 0

        fun isTransient(error: PlaybackException): Boolean = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> true
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> httpCode(error).let { it == 408 || it == 429 || it in 500..599 }
            else -> false
        }
    }
}
