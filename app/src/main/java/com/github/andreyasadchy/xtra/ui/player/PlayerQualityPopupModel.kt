package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.VideoQuality
import java.util.Locale

internal data class PlayerQualityPopupLabels(
    val auto: String,
    val source: String,
    val audioOnly: String,
    val chatOnly: String,
)

internal data class PlayerQualityPopupOption(
    val primaryLabel: String,
    val codecLabel: String?,
    val tag: String,
    val kind: Kind,
    val quality: VideoQuality,
) {
    enum class Kind { VIDEO, AUDIO_ONLY, CHAT_ONLY }
}

/** Pure option mapping for the Quality popup. */
internal object PlayerQualityPopupModel {

    fun build(
        qualities: List<VideoQuality>,
        labels: PlayerQualityPopupLabels,
    ): List<PlayerQualityPopupOption> {
        val showCodecs = qualities.any { quality ->
            val codec = quality.codecs.codecPrefix()
            codec !in setOf(null, "avc1", "mp4a")
        }
        val options = qualities.mapNotNull { quality ->
            val tag = quality.name ?: return@mapNotNull null
            if (tag.isNumericQualityFallback()) return@mapNotNull null
            val normalized = tag.normalizedPopupValue()
            val kind = when (normalized) {
                "audio_only" -> PlayerQualityPopupOption.Kind.AUDIO_ONLY
                "chat_only" -> PlayerQualityPopupOption.Kind.CHAT_ONLY
                else -> PlayerQualityPopupOption.Kind.VIDEO
            }
            PlayerQualityPopupOption(
                primaryLabel = when (normalized) {
                    "auto" -> labels.auto
                    "source" -> labels.source
                    "audio_only" -> labels.audioOnly
                    "chat_only" -> labels.chatOnly
                    else -> tag
                },
                codecLabel = if (showCodecs && kind == PlayerQualityPopupOption.Kind.VIDEO && normalized != "auto") {
                    codecDisplayName(quality.codecs)
                } else {
                    null
                },
                tag = tag,
                quality = quality,
                kind = kind,
            )
        }

        // A label is presentation, never identity. Disambiguate every member of a
        // duplicate set, then number only otherwise indistinguishable variants.
        val duplicateLabels = options.groupingBy { it.primaryLabel to it.codecLabel }.eachCount()
        val usedLabels = mutableSetOf<String>()
        val distinctOptions = options.map { option ->
            val duplicate = duplicateLabels.getValue(option.primaryLabel to option.codecLabel) > 1
            val bitrate = option.quality.bitrate?.takeIf { it > 0 }
                ?.let { "${it / 1000} Kbps" }
            val detail = listOfNotNull(option.codecLabel, bitrate.takeIf { duplicate }).joinToString(" · ").ifBlank { null }
            val baseLabel = listOfNotNull(option.primaryLabel, detail).joinToString(" ")
            var suffix = 1
            var label = baseLabel
            while (!usedLabels.add(label)) {
                suffix++
                label = "$baseLabel ($suffix)"
            }
            option.copy(codecLabel = if (suffix > 1) listOfNotNull(detail, "($suffix)").joinToString(" ") else detail)
        }
        val video = distinctOptions.filter { it.kind == PlayerQualityPopupOption.Kind.VIDEO }
        val utilities = distinctOptions
            .filter { it.kind != PlayerQualityPopupOption.Kind.VIDEO }
            .sortedBy { it.kind.ordinal }
        return video + utilities
    }

    fun codecDisplayName(codecs: String?): String? {
        return when (val codec = codecs.codecPrefix()) {
            null, "mp4a" -> null
            "av01" -> "AV1"
            "hev1", "hvc1" -> "H.265"
            "avc1" -> "H.264"
            else -> codec.uppercase(Locale.US)
        }
    }

    private fun String?.codecPrefix(): String? {
        val codecs = this?.split(',')?.mapNotNull {
            it.trim().substringBefore('.').lowercase(Locale.US).takeIf(String::isNotBlank)
        }.orEmpty()
        return codecs.firstOrNull { it !in setOf("mp4a", "opus") } ?: codecs.firstOrNull()
    }

    private fun String.normalizedPopupValue(): String = trim()
        .lowercase(Locale.US)
        .replace(' ', '_')
        .replace('-', '_')
}
