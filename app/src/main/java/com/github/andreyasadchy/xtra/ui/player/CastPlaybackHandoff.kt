package com.github.andreyasadchy.xtra.ui.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.source.MediaSource
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import com.google.common.net.InetAddresses

/** Retains only this session's current source, including its parser/HTTP configuration. */
@OptIn(UnstableApi::class)
internal class CastPlaybackHandoff(
    private val local: ExoPlayer,
    private val sourceFactoryChanged: (MediaSource.Factory?) -> Unit = {},
    /** Called on [main] when a castable live source has no receiver-loadable variant. */
    private val receiverUnavailable: () -> Unit = {},
    private val main: Executor = MAIN,
    private val background: Executor = PLAYLISTS,
    private val transferOnline: (Player, Player, MediaItem) -> Unit = ::transferState,
) {
    private var source: MediaSource? = null
    private var factory: MediaSource.Factory? = null
    private var loader: CastPlaylistLoader? = null
    private var ownedItem: MediaItem? = null
    /** Invalidates pending receiver playlist lookups whenever ownership or route changes. */
    private var request = 0
    private data class LocalSnapshot(val item: MediaItem, val position: Long, val play: Boolean, val speed: PlaybackParameters)
    private var rejected: LocalSnapshot? = null
    /** Restores local playback if the route returns before a receiver lookup completes. */
    private var pending: LocalSnapshot? = null

    fun transfer(from: Player, to: Player, reject: () -> Unit) {
        request++
        val item = from.currentMediaItem
        val lookup = pending
        pending = null
        if (to === local) {
            val snapshot = rejected ?: lookup
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
        } else if (item == null) {
            to.clearMediaItems()
        } else if (!canReceive(item)) {
            rejected = snapshot(item, from.currentPosition, from.playWhenReady, from.playbackParameters)
            to.clearMediaItems()
            reject()
        } else {
            // A previous rejected local-only Cast attempt must not override this newer session.
            rejected = null
            ownedItem = item
            if (!needsVariant(item)) {
                transferOnline(from, to, item)
            } else {
                val fallback = snapshot(item, from.currentPosition, from.playWhenReady, from.playbackParameters)
                val variant = localVariant(item)
                if (variant != null) transferOnline(from, to, item.withReceiverUri(variant))
                else resolve(item, fallback) {
                    to.playWhenReady = fallback.play
                    to.setMediaItem(it)
                }
            }
        }
    }

    fun setSource(active: Player, factory: MediaSource.Factory, item: MediaItem, loader: CastPlaylistLoader? = null): Boolean {
        if (!canSetSource(active, item)) return false
        val prepared = factory.createMediaSource(item)
        if (!canSetSource(active, prepared.mediaItem)) return false
        this.factory = factory
        this.loader = loader
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
        request++
        // Discard any pending fallback for an older, rejected source.
        rejected = null
        pending = null
        source = prepared
        val item = prepared.mediaItem
        ownedItem = item
        if (active.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            if (!needsVariant(item)) active.setMediaItem(item)
            // The service starts playback right after a source command.
            else resolve(item, snapshot(item, 0, true, PlaybackParameters.DEFAULT)) { active.setMediaItem(it) }
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

    private fun snapshot(item: MediaItem, position: Long, play: Boolean, speed: PlaybackParameters) =
        LocalSnapshot(item, if (CastReceiverVariants.isLive(item)) C.TIME_UNSET else position.coerceAtLeast(0), play, speed)

    /** The local player already parsed this source's playlist while it was playing. */
    private fun localVariant(item: MediaItem): android.net.Uri? {
        if (local.currentMediaItem?.localConfiguration?.uri != item.localConfiguration?.uri) return null
        val playlist = (local.currentManifest as? HlsManifest)?.multivariantPlaylist ?: return null
        return CastReceiverVariants.select(playlist, selectedVideoFormat())
    }

    /** The user's fixed local quality; Auto has no override and casts the best variant. */
    private fun selectedVideoFormat(): Format? = local.trackSelectionParameters.overrides.values
        .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.trackIndices.isNotEmpty() }
        ?.let { it.mediaTrackGroup.getFormat(it.trackIndices[0]) }

    private fun resolve(item: MediaItem, fallback: LocalSnapshot, load: (MediaItem) -> Unit) {
        val token = ++request
        val loader = loader
        val uri = item.localConfiguration?.uri
        if (loader == null || uri == null) {
            unavailable(fallback)
            return
        }
        val preferred = selectedVideoFormat()
        pending = fallback
        background.execute {
            val variant = try {
                CastReceiverVariants.select(loader.load(uri), preferred)
            } catch (e: Exception) {
                Log.w(TAG, "Cast receiver playlist unavailable", e)
                null
            }
            main.execute {
                if (token != request) return@execute
                pending = null
                if (variant != null) load(item.withReceiverUri(variant)) else unavailable(fallback)
            }
        }
    }

    /** Session end returns to local playback through [rejected]. */
    private fun unavailable(fallback: LocalSnapshot) {
        rejected = fallback
        receiverUnavailable()
    }

    fun clear() {
        request++
        source = null; factory = null; loader = null; ownedItem = null; rejected = null; pending = null
        sourceFactoryChanged(null)
    }

    companion object {
        private const val TAG = "CastPlaybackHandoff"
        private val MAIN = Executor { Handler(Looper.getMainLooper()).post(it) }
        private val PLAYLISTS: Executor by lazy { Executors.newSingleThreadExecutor() }

        private fun transferState(from: Player, to: Player, item: MediaItem) {
            PlayerTransferState.fromPlayer(from).buildUpon()
                .setMediaItems(listOf(item))
                .setCurrentMediaItemIndex(0)
                .build().setToPlayer(to)
        }

        /** Live HLS must be swapped for a variant before a receiver can fetch it. */
        private fun needsVariant(item: MediaItem) = CastReceiverVariants.isHls(item) && CastReceiverVariants.isLive(item)

        fun canSetSource(active: Player, item: MediaItem?): Boolean =
            active.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE || canReceive(item)

        /** Twitch VoD playlists and segments reject receiver origins, so VoDs stay on the phone. */
        fun canReceive(item: MediaItem?): Boolean =
            canCast(item) && (!CastReceiverVariants.isHls(item) || CastReceiverVariants.isLive(item))

        fun canCast(item: MediaItem?): Boolean {
            val uri = item?.localConfiguration?.uri ?: return false
            // Older Android Uri implementations split IPv6 hosts at the first colon.
            val host = try { URI(uri.toString()).host?.lowercase() } catch (_: URISyntaxException) { null } ?: return false
            val hostname = host.removeSuffix(".")
            if (uri.scheme?.lowercase() !in listOf("https", "http") || hostname.isBlank() ||
                hostname == "localhost" || hostname.endsWith(".localhost")) return false
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
