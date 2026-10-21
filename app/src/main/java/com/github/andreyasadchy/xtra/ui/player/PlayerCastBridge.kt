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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.github.andreyasadchy.xtra.R
import com.google.android.gms.cast.framework.CastContext

@OptIn(UnstableApi::class)
internal class PlayerCastBridge(private val context: Context, private val local: ExoPlayer, enabled: Boolean) {
    val handoff = CastPlaybackHandoff(local)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false

    val player: Player = if (enabled) try {
        CastPlayer.Builder(context)
            .setLocalPlayer(local)
            .setRemotePlayer(RemoteCastPlayer.Builder(context)
                .setSeekBackIncrementMs(local.seekBackIncrement)
                .setSeekForwardIncrementMs(local.seekForwardIncrement).build())
            .setTransferCallback { source, target ->
                if (!closed) handoff.transfer(source, target) {
                    handler.post {
                        if (!closed) {
                            Toast.makeText(context, R.string.cast_local_only, Toast.LENGTH_LONG).show()
                            CastContext.getSharedInstance(context).sessionManager.endCurrentSession(true)
                        }
                    }
                }
            }.build()
    } catch (error: RuntimeException) {
        Log.w("PlayerCastBridge", "Cast unavailable; local playback remains available", error)
        local
    } else local

    fun dispose() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        handoff.clear()
        endSession()
    }

    fun endSession() {
        if (player !== local) {
            CastContext.getSharedInstance(context).sessionManager.endCurrentSession(true)
        }
    }
}
