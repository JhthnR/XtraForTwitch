package com.github.andreyasadchy.xtra.ui.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.exoplayer.upstream.ParsingLoadable
import com.google.android.gms.cast.MediaQueueItem
import kotlin.math.abs

/**
 * The URL a Cast receiver loads in place of the local source URL.
 *
 * Receivers fetch HLS from a browser. Twitch's usher multivariant playlists (and VoD CDNs)
 * only allow Twitch origins, while live variant playlists and segments allow any origin.
 */
internal data class CastReceiverUri(val uri: Uri)

internal fun MediaItem.withReceiverUri(uri: Uri): MediaItem = buildUpon().setTag(CastReceiverUri(uri)).build()

/** Keeps the local identity in contentId/customData while the receiver loads [CastReceiverUri]. */
@OptIn(UnstableApi::class)
internal class CastReceiverMediaItemConverter(
    private val delegate: MediaItemConverter = DefaultMediaItemConverter(),
) : MediaItemConverter {
    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val queueItem = delegate.toMediaQueueItem(mediaItem)
        val receiver = mediaItem.localConfiguration?.tag as? CastReceiverUri ?: return queueItem
        queueItem.media?.writer?.setContentUrl(receiver.uri.toString())
        return queueItem
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = delegate.toMediaItem(mediaQueueItem)
}

/** Runs off the main thread with the source's own HTTP stack. */
@OptIn(UnstableApi::class)
internal fun interface CastPlaylistLoader {
    fun load(uri: Uri): HlsPlaylist
}

@OptIn(UnstableApi::class)
internal fun castPlaylistLoader(factory: DataSource.Factory) = CastPlaylistLoader { uri ->
    ParsingLoadable.load(factory.createDataSource(), HlsPlaylistParser(), uri, C.DATA_TYPE_MANIFEST)
}

@OptIn(UnstableApi::class)
internal object CastReceiverVariants {
    fun isHls(item: MediaItem?): Boolean = item?.localConfiguration?.let {
        Util.inferContentTypeForUriAndMimeType(it.uri, it.mimeType) == C.CONTENT_TYPE_HLS
    } == true

    fun isLive(item: MediaItem?): Boolean =
        item != null && item.liveConfiguration != MediaItem.LiveConfiguration.UNSET

    /** A media playlist is already a single variant; otherwise follow the local quality choice. */
    fun select(playlist: HlsPlaylist, preferred: Format?): Uri? = when (playlist) {
        is HlsMediaPlaylist -> Uri.parse(playlist.baseUri)
        is HlsMultivariantPlaylist -> select(playlist, preferred)
        else -> null
    }

    fun select(playlist: HlsMultivariantPlaylist, preferred: Format?): Uri? {
        val variants = playlist.variants
        val video = variants.filter { it.format.height > 0 }
        if (video.isEmpty()) return variants.firstOrNull()?.url
        // Receivers decode H.264 reliably; HEVC/AV1 variants are a phone-side opt-in.
        val avc = video.filter { it.format.codecs?.contains("avc1", ignoreCase = true) != false }.ifEmpty { video }
        val height = preferred?.height?.takeIf { it > 0 } ?: return avc.maxBy { it.format.bitrate }.url
        avc.filter { it.format.height == height }.minByOrNull { abs(it.format.frameRate - preferred.frameRate) }
            ?.let { return it.url }
        return (avc.filter { it.format.height < height }.maxByOrNull { it.format.bitrate }
            ?: avc.minBy { it.format.bitrate }).url
    }
}
