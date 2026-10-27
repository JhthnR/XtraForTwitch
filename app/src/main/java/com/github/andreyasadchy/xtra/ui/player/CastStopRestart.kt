package com.github.andreyasadchy.xtra.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand

/**
 * Ends the Cast session for a local-only source, then restarts it once playback is local.
 * Receiver media is cleared first so the session end cannot restore it on the phone.
 */
@OptIn(UnstableApi::class)
internal class CastStopRestart(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    private var generation = 0
    private var pending: Pair<MediaController, Player.Listener>? = null
    val isPending get() = pending != null

    fun start(controller: MediaController, restart: () -> Unit, failed: () -> Unit) {
        cancel()
        val request = generation
        fun finish(success: Boolean) {
            if (request != generation) return
            cancel()
            if (success) restart() else failed()
        }
        val listener = object : Player.Listener {
            override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) finish(true)
            }
        }
        pending = controller to listener
        controller.addListener(listener)
        controller.stop()
        controller.clearMediaItems()
        controller.sendCustomCommand(SessionCommand(PlaybackService.END_CAST_SESSION, Bundle.EMPTY), Bundle.EMPTY)
            .addListener({
                if (controller.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) finish(true)
            }, handler::post)
        handler.postDelayed({ finish(false) }, timeoutMs)
    }

    fun cancel() {
        generation++
        handler.removeCallbacksAndMessages(null)
        pending?.let { (controller, listener) -> controller.removeListener(listener) }
        pending = null
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
