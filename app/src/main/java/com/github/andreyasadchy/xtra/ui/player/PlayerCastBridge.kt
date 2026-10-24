package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.Player
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.github.andreyasadchy.xtra.R
import com.google.android.gms.cast.framework.CastContext
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class PlayerCastBridge(
    private val context: Context,
    enabled: Boolean,
    private val createLocal: () -> ExoPlayer,
    private val sourceFactoryChanged: (MediaSource.Factory?) -> Unit = {},
    private val endCurrentSession: () -> Unit = {
        // This accessor does not initialize Cast for default-off playback.
        @Suppress("DEPRECATION")
        CastContext.getSharedInstance()?.sessionManager?.endCurrentSession(true)
    },
    private val createCast: (ExoPlayer, CastPlayer.TransferCallback) -> Player = { local, transfer ->
        CastPlayer.Builder(context).setLocalPlayer(local)
            .setRemotePlayer(RemoteCastPlayer.Builder(context)
                .setSeekBackIncrementMs(local.seekBackIncrement)
                .setSeekForwardIncrementMs(local.seekForwardIncrement).build())
            .setTransferCallback(transfer).build()
    },
) {
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var generation = 0
    private var configured = enabled
    private var local = createLocal()
    var handoff = CastPlaybackHandoff(local, sourceFactoryChanged)
        private set
    private var active = buildPlayer(enabled)
    private val routing = RoutePlayer(active)
    val player: Player get() = routing
    val castAvailable get() = active !== local

    private fun buildPlayer(enabled: Boolean): Player = if (enabled) try {
        val routeGeneration = generation
        val routeHandoff = handoff
        createCast(local, CastPlayer.TransferCallback { source, target ->
            if (!closed && generation == routeGeneration) routeHandoff.transfer(source, target) {
                handler.post {
                    if (!closed && generation == routeGeneration) {
                        Toast.makeText(context, R.string.cast_local_only, Toast.LENGTH_LONG).show()
                        endCurrentSession()
                    }
                }
            }
        })
    } catch (error: RuntimeException) {
        Log.w("PlayerCastBridge", "Cast unavailable; local playback remains available", error)
        local
    } else local

    /** Only called before an explicit source command, never in the middle of playback. */
    fun configure(enabled: Boolean) {
        if (closed || configured == enabled) return
        val previous = active
        val selection = previous.trackSelectionParameters
        previous.pause()
        previous.stop()
        previous.clearMediaItems()
        generation++
        handler.removeCallbacksAndMessages(null)
        handoff.clear()
        endCurrentSession()
        local = createLocal().also { it.trackSelectionParameters = selection }
        handoff = CastPlaybackHandoff(local, sourceFactoryChanged)
        active = buildPlayer(enabled)
        routing.replace(active)
        previous.release()
        configured = enabled
    }

    fun dispose() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        handoff.clear()
        endSession()
    }

    fun endSession() {
        // Explicit close must invalidate pending restores before SDK disconnect callbacks.
        handler.removeCallbacksAndMessages(null)
        handoff.clear()
        routing.detachOutput()
        endCurrentSession()
    }

    private class RoutePlayer(player: Player) : ForwardingSimpleBasePlayer(player) {
        private var videoOutput: Any? = null
        private var replayOutput = false
        private val outputListener = object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = updateOutput()
        }

        init { player.addListener(outputListener) }

        override fun handleSetVideoOutput(output: Any): ListenableFuture<*> {
            videoOutput = output
            replayOutput = false
            return super.handleSetVideoOutput(output)
        }

        override fun handleClearVideoOutput(output: Any?): ListenableFuture<*> {
            if (output == null || output === videoOutput) { videoOutput = null; replayOutput = false }
            return super.handleClearVideoOutput(output)
        }

        fun replace(player: Player) {
            // Media3 setPlayer changes listeners/state, not the attached video output.
            getPlayer().removeListener(outputListener)
            if (getPlayer().isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) {
                videoOutput?.let { super.handleClearVideoOutput(it) }
            }
            setPlayer(player)
            player.addListener(outputListener)
            replayOutput = true
            updateOutput()
        }

        fun detachOutput() {
            videoOutput = null
            replayOutput = true
            updateOutput()
        }

        private fun updateOutput() {
            if (replayOutput && getPlayer().isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) {
                replayOutput = false
                videoOutput?.let { super.handleSetVideoOutput(it) } ?: super.handleClearVideoOutput(null)
            }
        }

        override fun handleRelease(): ListenableFuture<*> {
            getPlayer().removeListener(outputListener)
            videoOutput = null
            return super.handleRelease()
        }
    }
}

/** Stored phone settings must never overwrite receiver controls on a source switch. */
@OptIn(UnstableApi::class)
internal fun Player.applyLocalPlaybackDefaults(volume: Float, speed: Float) {
    if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return
    this.volume = volume
    setPlaybackSpeed(speed)
}
