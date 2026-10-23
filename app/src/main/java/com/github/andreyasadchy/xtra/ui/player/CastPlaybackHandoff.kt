package com.github.andreyasadchy.xtra.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.cast.CastPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import java.net.URI
import java.net.URISyntaxException
import com.google.common.net.InetAddresses

/** Retains only this session's current source, including its parser/HTTP configuration. */
@OptIn(UnstableApi::class)
internal class CastPlaybackHandoff(
    private val local: ExoPlayer,
    private val sourceFactoryChanged: (MediaSource.Factory?) -> Unit = {},
    private val transferOnline: (Player, Player) -> Unit = CastPlayer.TransferCallback.DEFAULT::transferState,
) {
    private var source: MediaSource? = null
    private var factory: MediaSource.Factory? = null
    private var ownedItem: MediaItem? = null
    private data class LocalSnapshot(val item: MediaItem, val position: Long, val play: Boolean, val speed: PlaybackParameters)
    private var rejected: LocalSnapshot? = null

    fun transfer(from: Player, to: Player, reject: () -> Unit) {
        if (to === local) {
            val snapshot = rejected
            rejected = null
            if (snapshot != null) {
                val prepared = configuredSource(snapshot.item)
                if (prepared != null) local.setMediaSource(prepared, snapshot.position)
                else local.setMediaItem(snapshot.item, snapshot.position)
                local.playbackParameters = snapshot.speed
                local.prepare()
                local.playWhenReady = snapshot.play
            } else if (!restoreFrom(from)) {
                // Never adopt unrelated receiver content into a Twitch player/chat.
                local.clearMediaItems()
            }
        } else if (from.currentMediaItem == null) {
            to.clearMediaItems()
        } else if (!canCast(from.currentMediaItem)) {
            from.currentMediaItem?.let {
                rejected = LocalSnapshot(it, from.currentPosition.coerceAtLeast(0), from.playWhenReady, from.playbackParameters)
            }
            to.clearMediaItems()
            reject()
        } else {
            // A previous rejected local-only Cast attempt must not override this newer session.
            rejected = null
            ownedItem = from.currentMediaItem
            transferOnline(from, to)
        }
    }

    fun setSource(active: Player, factory: MediaSource.Factory, item: MediaItem): Boolean {
        if (!canSetSource(active, item)) return false
        val prepared = factory.createMediaSource(item)
        if (!canSetSource(active, prepared.mediaItem)) return false
        this.factory = factory
        sourceFactoryChanged(factory)
        installSource(active, prepared)
        return true
    }

    internal fun setSource(active: Player, prepared: MediaSource): Boolean {
        if (!canSetSource(active, prepared.mediaItem)) return false
        installSource(active, prepared)
        return true
    }

    private fun installSource(active: Player, prepared: MediaSource) {
        // Discard any pending fallback for an older, rejected source.
        rejected = null
        source = prepared
        ownedItem = prepared.mediaItem
        if (active.deviceInfo.playbackType == androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            active.setMediaItem(prepared.mediaItem)
        } else {
            local.setMediaSource(prepared)
        }
    }

    /** Return false for a receiver item that this ThystTV session does not own. */
    fun restoreFrom(remote: Player): Boolean {
        val item = ownedItem ?: return false
        if (remote.currentMediaItem?.localConfiguration?.uri != item.localConfiguration?.uri) return false
        val prepared = configuredSource(item) ?: return false
        val isLive = prepared.mediaItem.liveConfiguration != MediaItem.LiveConfiguration.UNSET
        local.setMediaSource(prepared, if (isLive) C.TIME_UNSET else remote.currentPosition.coerceAtLeast(0))
        local.playbackParameters = remote.playbackParameters
        local.prepare()
        local.playWhenReady = remote.playWhenReady
        return true
    }

    private fun configuredSource(item: MediaItem): MediaSource? =
        source?.takeIf { it.mediaItem.localConfiguration?.uri == item.localConfiguration?.uri }
            ?: factory?.createMediaSource(item)?.also { source = it }

    fun clear() {
        source = null; factory = null; ownedItem = null; rejected = null
        sourceFactoryChanged(null)
    }

    companion object {
        fun canSetSource(active: Player, item: MediaItem?): Boolean =
            active.deviceInfo.playbackType != androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE || canCast(item)

        fun canCast(item: MediaItem?): Boolean {
            val uri = item?.localConfiguration?.uri ?: return false
            // Older Android Uri implementations split IPv6 hosts at the first colon.
            val host = try { URI(uri.toString()).host?.lowercase() } catch (_: URISyntaxException) { null } ?: return false
            if (uri.scheme?.lowercase() !in listOf("https", "http") || host.isBlank() || host == "localhost") return false
            val literal = host.removeSurrounding("[", "]")
            // Parse numeric literals only: never invoke a DNS resolver on the main thread.
            if (':' in literal || literal.all { it.isDigit() || it == '.' }) {
                // Guava validates an IP literal without DNS; malformed numeric hosts
                // and noncanonical integer IPv4 forms are intentionally rejected.
                if (!InetAddresses.isInetAddress(literal)) return false
                val address = InetAddresses.forString(literal)
                return !address.isLoopbackAddress && !address.isAnyLocalAddress
            }
            return true
        }
    }
}
