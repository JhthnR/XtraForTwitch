package com.github.andreyasadchy.xtra.model

import kotlinx.serialization.Serializable
import java.net.URI

/**
 * Twitch's seek-preview storyboard for a VoD: sprite sheets of small frames, one frame
 * every [intervalMs], laid out left to right and top to bottom, [columns] by [rows] per sheet.
 */
class VideoStoryboard private constructor(
    val images: List<String>,
    val count: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    val rows: Int,
    val intervalMs: Double,
) {

    data class Frame(val imageUrl: String, val left: Int, val top: Int, val width: Int, val height: Int)

    fun frameAt(positionMs: Long): Frame? {
        val index = (positionMs.coerceAtLeast(0) / intervalMs).toInt().coerceIn(0, count - 1)
        val perImage = columns * rows
        val imageUrl = images.getOrNull(index / perImage) ?: return null
        val cell = index % perImage
        return Frame(imageUrl, (cell % columns) * frameWidth, (cell / columns) * frameHeight, frameWidth, frameHeight)
    }

    @Serializable
    class Info(
        val count: Int = 0,
        val width: Int = 0,
        val height: Int = 0,
        val cols: Int = 0,
        val rows: Int = 0,
        val interval: Double = 0.0,
        val quality: String? = null,
        val images: List<String> = emptyList(),
    )

    companion object {
        /** `.../storyboards/<id>-strip-0.jpg` → `.../storyboards/<id>-info.json`, the `seekPreviewsURL` of the VoD. */
        fun infoUrl(animatedPreviewUrl: String?): String? {
            val uri = runCatching { URI(animatedPreviewUrl ?: return null) }.getOrNull() ?: return null
            if (uri.scheme !in listOf("https", "http") || uri.host == null) return null
            val path = uri.path ?: return null
            if (!path.contains("/storyboards/")) return null
            val match = Regex("(.+)-strip-\\d+\\.jpg").matchEntire(path.substringAfterLast('/')) ?: return null
            return uri.resolve("${match.groupValues[1]}-info.json").toString()
        }

        /** Picks the high quality set, or the widest valid one. Image names are relative to [infoUrl]. */
        fun from(infoUrl: String, info: List<Info>): VideoStoryboard? {
            val valid = info.filter {
                it.count > 0 && it.width in 1..1024 && it.height in 1..1024 &&
                    it.cols in 1..100 && it.rows in 1..100 &&
                    it.width.toLong() * it.height * it.cols * it.rows <= 16_000_000 &&
                    it.interval.isFinite() && it.interval > 0 && (it.interval * 1000).isFinite() &&
                    it.images.isNotEmpty() && it.images.none(String::isBlank)
            }
            val set = valid.find { it.quality == "high" } ?: valid.maxByOrNull { it.width } ?: return null
            val base = runCatching { URI(infoUrl) }.getOrNull() ?: return null
            val images = runCatching { set.images.map { base.resolve(it) } }.getOrNull() ?: return null
            if (images.any { it.scheme !in listOf("https", "http") || it.host == null }) return null
            return VideoStoryboard(
                images = images.map(URI::toString),
                count = set.count,
                frameWidth = set.width,
                frameHeight = set.height,
                columns = set.cols,
                rows = set.rows,
                intervalMs = set.interval * 1000,
            )
        }
    }
}
