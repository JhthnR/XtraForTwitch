package com.github.andreyasadchy.xtra.ui.player

import androidx.annotation.MainThread
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import java.lang.ref.WeakReference

/** The service owns this reference; it survives activity/fragment recreation. */
@MainThread
internal object PlaybackSessionOwner {
    private var owner: WeakReference<Player>? = null
    private var bridge: WeakReference<PlayerCastBridge>? = null

    fun register(player: Player, castBridge: PlayerCastBridge? = null) {
        owner = WeakReference(player)
        bridge = castBridge?.let(::WeakReference)
    }

    fun unregister(player: Player) {
        if (owner?.get() === player) { owner = null; bridge = null }
    }

    fun hasMedia(): Boolean = owner?.get()?.let {
        it.currentMediaItem != null || bridge?.get()?.handoff?.hasPendingSource == true
    } == true

    fun hasRemoteMedia(): Boolean = owner?.get()?.let {
        hasMedia() && it.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
    } == true
}
