package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.model.VideoQuality
import java.net.URI
import kotlin.math.roundToInt

/** Dual-format streams also offer portrait variants; the player shows landscape video. */
internal fun isPortraitVariant(stableVariantId: String?) = stableVariantId?.endsWith("-portrait") == true

/** Twitch withholds these unavailable variants for an unrequested codec or the portrait format. */
internal fun isSkippedUnavailableReason(reason: String?) =
    reason == "FR_CODEC_NOT_REQUESTED" || reason == "FR_DUALFORMAT_INCOMPATIBLE"

/** Read attributes per variant so optional CODECS/BANDWIDTH cannot shift onto another URL. */
internal fun parsePlaylistQualities(playlist: String, baseUrl: String? = null): List<VideoQuality> {
    val attributes = Regex("""(?:^|,)([A-Z0-9-]+)=(?:"([^"]*)"|([^,]*))""")
    fun parseAttributes(line: String): Map<String, String> = attributes.findAll(line.substringAfter(':'))
        .associate { it.groupValues[1] to (it.groups[2]?.value ?: it.groupValues[3]) }
    val mediaNames = playlist.lineSequence().map(String::trim).filter { it.startsWith("#EXT-X-MEDIA:") }
        .map(::parseAttributes).mapNotNull { attrs ->
            val group = attrs["GROUP-ID"] ?: return@mapNotNull null
            attrs["NAME"]?.let { group to it }
        }.toMap()
    var pending: Map<String, String>? = null
    return buildList {
        for (raw in playlist.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> pending = parseAttributes(line)
                line.isNotEmpty() && !line.startsWith('#') -> {
                    val attrs = pending ?: continue
                    pending = null
                    if (isPortraitVariant(attrs["STABLE-VARIANT-ID"])) continue
                    val url = runCatching {
                        val uri = URI(line)
                        if (uri.isAbsolute) uri else baseUrl?.let { URI(it).resolve(uri) }
                    }.getOrNull()?.takeIf { it.scheme == "https" || it.scheme == "http" }?.toString() ?: continue
                    val height = attrs["RESOLUTION"]?.substringAfter('x')?.toIntOrNull()?.takeIf { it > 0 }
                    val frameRate = attrs["FRAME-RATE"]?.toFloatOrNull()?.takeIf { it > 0f && it.isFinite() }
                    val codecs = attrs["CODECS"]
                    val name = attrs["IVS-NAME"] ?: attrs["NAME"] ?: mediaNames[attrs["VIDEO"]]
                        ?: height?.let { "${it}p" + (frameRate?.roundToInt()?.takeIf { fps -> fps > 30 } ?: "") }
                        ?: attrs["VIDEO"]?.takeIf { it == "audio_only" || it == "chunked" || it == "source" }
                        ?: if (codecs?.split(',')?.all { it.trim().startsWith("mp4a") || it.trim().startsWith("opus") } == true) "audio_only" else null
                    add(VideoQuality(
                        name = if (name == "chunked") "source" else name,
                        codecs = codecs,
                        url = url,
                        bitrate = (attrs["BANDWIDTH"] ?: attrs["AVERAGE-BANDWIDTH"])?.toIntOrNull()?.takeIf { it > 0 },
                        resolution = height,
                        frameRate = frameRate,
                    ))
                }
            }
        }
    }
}
