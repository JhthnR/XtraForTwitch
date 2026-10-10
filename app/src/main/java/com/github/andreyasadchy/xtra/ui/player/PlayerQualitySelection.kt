package com.github.andreyasadchy.xtra.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.github.andreyasadchy.xtra.model.VideoQuality
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
internal fun Format.toVideoQuality(name: String, url: String): VideoQuality = VideoQuality(
    name = name,
    codecs = codecs,
    url = url,
    bitrate = bitrate.takeIf { it > 0 },
    resolution = height.takeIf { it > 0 },
    frameRate = frameRate.takeIf { it > 0 },
    formatId = id,
)

/** Match across all supported video groups, including separate codec groups. */
@OptIn(UnstableApi::class)
internal fun selectQualityTrack(quality: VideoQuality, groups: List<Tracks.Group>): TrackSelectionOverride? {
    val tracks = groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it }
    }
    val index = selectQualityFormatIndex(quality, tracks.map { (group, track) -> group.getTrackFormat(track) }) ?: return null
    val (group, track) = tracks[index]
    return TrackSelectionOverride(group.mediaTrackGroup, track)
}

/**
 * Exact variant metadata wins. Otherwise pick the best rung at or below the request,
 * ranked by resolution then frame rate. Codec family and bitrate only break ties within
 * a rung: a saved bitrate from another stream must never push playback down a rung.
 */
@OptIn(UnstableApi::class)
internal fun selectQualityFormatIndex(quality: VideoQuality, formats: List<Format>): Int? {
    if (formats.isEmpty()) return null
    val resolution = quality.resolution ?: quality.name?.substringBefore('p')?.toIntOrNull()
    val frameRate = quality.frameRate ?: quality.name?.takeIf { it.substringBefore('p').toIntOrNull() != null }
        ?.let { it.substringAfter('p', "").toFloatOrNull() ?: 30f }
    val family = quality.codecs.codecFamily()
    if (resolution != null || quality.bitrate != null || quality.formatId != null) {
        val exact = formats.indices.filter {
            val format = formats[it]
            (resolution == null || resolution == format.height) &&
                (frameRate == null || format.frameRate <= 0 || abs(frameRate - format.frameRate) < 1f) &&
                (family == null || family == format.codecs.codecFamily()) &&
                (quality.bitrate == null || quality.bitrate == format.bitrate)
        }
        exact.find { quality.formatId != null && formats[it].id == quality.formatId }?.let { return it }
        // A name-only choice saved by 1.3.1 matches every same-named duplicate; take the best one.
        pickByBitrate(exact, quality.bitrate) { formats[it].bitrate }?.let { return it }
    }
    val rungOrder = compareBy<Int> { formats[it].height }.thenBy { formats[it].frameRate.roundToInt() }
    val eligible = formats.indices.filter {
        val format = formats[it]
        resolution == null || format.height in 1 until resolution ||
            (format.height == resolution && (frameRate == null || format.frameRate <= 0 || format.frameRate < frameRate + 1f))
    }
    // When every variant exceeds the request, the lowest rung is the closest.
    val rung = eligible.maxWithOrNull(rungOrder) ?: formats.indices.minWith(rungOrder)
    val sameRung = formats.indices.filter { rungOrder.compare(it, rung) == 0 }
    val preferred = sameRung.filter { family != null && formats[it].codecs.codecFamily() == family }.ifEmpty { sameRung }
    return pickByBitrate(preferred, quality.bitrate) { formats[it].bitrate }
}

/** Highest known bitrate at or under the request, else the lowest above it. */
private fun <T> pickByBitrate(items: List<T>, requested: Int?, bitrate: (T) -> Int?): T? {
    val known = items.filter { (bitrate(it) ?: 0) > 0 }
    if (known.isEmpty()) return items.firstOrNull()
    if (requested == null) return known.maxBy { bitrate(it)!! }
    return known.filter { bitrate(it)!! <= requested }.maxByOrNull { bitrate(it)!! } ?: known.minBy { bitrate(it)!! }
}

/** Compare codec families, not profile/level strings that differ between channels and rungs. */
private fun String?.codecFamily(): String? {
    val codecs = this?.split(',')?.map { it.trim().substringBefore('.').lowercase(Locale.US) }?.filter { it.isNotEmpty() }.orEmpty()
    return when (val codec = codecs.firstOrNull { it != "mp4a" && it != "opus" } ?: codecs.firstOrNull()) {
        "avc1", "avc3" -> "h264"
        "hev1", "hvc1" -> "h265"
        "av01" -> "av1"
        else -> codec
    }
}

internal fun selectPreferredQuality(qualities: List<VideoQuality>, target: String?): VideoQuality? {
    val name = target?.substringBefore(' ')?.lowercase(Locale.US) ?: return null
    if (name in setOf("auto", "audio_only", "chat_only")) return qualities.find { it.name == name }
    val videos = qualities.filter { it.name !in setOf("auto", "audio_only", "chat_only") }
        .sortedWith(compareByDescending<VideoQuality> { it.resolution ?: it.name?.substringBefore('p')?.toIntOrNull() ?: 0 }
            .thenByDescending { it.frameRate ?: it.name?.substringAfter('p', "")?.toFloatOrNull() ?: 30f }
            .thenByDescending { it.bitrate ?: 0 })
    if (name == "source") return videos.find { it.name == "source" } ?: videos.firstOrNull()
    val resolution = name.substringBefore('p').toIntOrNull() ?: return null
    val fps = name.substringAfter('p', "").toFloatOrNull() ?: 30f
    return videos.firstOrNull {
        val height = it.resolution ?: it.name?.substringBefore('p')?.toIntOrNull()
        val rate = it.frameRate ?: it.name?.substringAfter('p', "")?.toFloatOrNull() ?: 30f
        height != null && (height < resolution || (height == resolution && floor(rate) <= fps))
    } ?: videos.lastOrNull()
}

/** Signed playlist URLs can change between choosing a recording and the worker starting. */
@OptIn(UnstableApi::class)
internal fun selectDownloadQuality(qualities: List<VideoQuality>, requested: VideoQuality): VideoQuality? {
    val name = requested.name?.let { if (it.startsWith("audio", true)) "audio_only" else it }
    if (name in setOf("auto", "chat_only")) return qualities.find { it.name == name }
    val sameName = qualities.filter { it.name == name }
    val path = requested.url?.let { runCatching { java.net.URI(it).path }.getOrNull() }?.takeIf { it.isNotBlank() }
    fun matchesPath(quality: VideoQuality): Boolean = path != null && runCatching { java.net.URI(quality.url.orEmpty()).path }.getOrNull() == path
    val family = requested.codecs.codecFamily()
    val exact = sameName.filter {
        (requested.codecs == null || requested.codecs == it.codecs) &&
            (requested.bitrate == null || requested.bitrate == it.bitrate)
    }
    exact.find(::matchesPath)?.let { return it }
    exact.firstOrNull()?.let { return it }
    sameName.find { matchesPath(it) && (family == null || it.codecs == null || family == it.codecs.codecFamily()) }?.let { return it }
    if (sameName.isNotEmpty()) {
        val preferred = sameName.filter { family != null && it.codecs.codecFamily() == family }.ifEmpty { sameName }
        return pickByBitrate(preferred, requested.bitrate) { it.bitrate }
    }
    if (name == "audio_only") return null
    val videos = qualities.filter { it.name !in setOf("auto", "audio_only", "chat_only") }
    val formats = videos.map {
        Format.Builder()
            .setHeight(it.resolution ?: it.name?.substringBefore('p')?.toIntOrNull() ?: Format.NO_VALUE)
            .setFrameRate(it.frameRate ?: it.name?.substringAfter('p', "")?.toFloatOrNull() ?: 30f)
            .setPeakBitrate(it.bitrate ?: Format.NO_VALUE)
            .setCodecs(it.codecs)
            .setId(it.formatId)
            .build()
    }
    return selectQualityFormatIndex(requested.copy(name = name), formats)?.let { videos[it] }
}

internal fun preserveRefreshedQuality(qualities: List<VideoQuality>, previous: VideoQuality?, userChangedQuality: Boolean): VideoQuality? =
    previous?.takeIf { userChangedQuality || it.name in setOf("audio_only", "chat_only") }
        ?.let { selectDownloadQuality(qualities, it) }
