package com.github.andreyasadchy.xtra.ui.player

import androidx.annotation.MainThread
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import java.lang.ref.WeakReference

/** The service owns this reference; it survives activity/fragment recreation. */
@MainThread
internal object PlaybackSessionOwner {
    private var owner: WeakReference<Player>? = null

    fun register(player: Player) { owner = WeakReference(player) }

    fun unregister(player: Player) {
        if (owner?.get() === player) owner = null
    }

    fun hasMedia(): Boolean = owner?.get()?.currentMediaItem != null

    fun hasRemoteMedia(): Boolean = owner?.get()?.let {
        it.currentMediaItem != null && it.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
    } == true
}
