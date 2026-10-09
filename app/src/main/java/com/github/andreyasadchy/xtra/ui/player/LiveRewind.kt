package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import kotlin.math.abs
import kotlin.math.max

/**
 * Recording matching adapted from thiscallnet/Xtra LiveRewind.kt (fbffe567), AGPL-3.0.
 * ThystTV deliberately uses its established single-player VoD handoff, not a DVR
 * buffer or a second playback service. A recording is not guaranteed to exist.
 */
internal data class LiveRewindCandidate(
    val video: Video,
    val createdAtMs: Long,
    val durationMs: Long,
)

internal data class PreparedLiveRewind(
    val origin: Stream,
    val video: Video,
    val playlistUrl: String,
    val positionMs: Long,
)

internal object LiveRewind {
    const val ORIGIN = "liveRewindOrigin"
    const val PLAYLIST = "liveRewindPlaylist"
    const val POSITION = "liveRewindPosition"
    const val MAX_START_DELTA_MS = 180_000L
    const val DURATION_QUANTUM_MS = 10_000L

    fun sameSession(expectedId: String?, expectedStart: String?, actualId: String?, actualStart: String?): Boolean =
        !actualId.isNullOrBlank() && !actualStart.isNullOrBlank() &&
            (expectedId.isNullOrBlank() || expectedId == actualId) &&
            (expectedStart.isNullOrBlank() || expectedStart == actualStart)

    fun selectRecording(candidates: List<LiveRewindCandidate>, streamStartMs: Long, nowMs: Long): LiveRewindCandidate? {
        if (streamStartMs <= 0 || streamStartMs > nowMs) return null
        val age = nowMs - streamStartMs
        val tolerance = max(120_000L, age / 10)
        return candidates.asSequence().filter {
            !it.video.id.isNullOrBlank() && it.createdAtMs > 0 && it.durationMs > 0 &&
                abs(it.createdAtMs - streamStartMs) <= MAX_START_DELTA_MS &&
                // A short archive from a just-ended broadcast can satisfy both
                // donor tolerances. It must actually extend into this session.
                (it.createdAtMs >= streamStartMs || it.durationMs > streamStartMs - it.createdAtMs) &&
                abs(it.durationMs - age) <= tolerance
        }.minWithOrNull(compareBy<LiveRewindCandidate> { abs(it.createdAtMs - streamStartMs) }
            .thenByDescending { it.createdAtMs })
    }

    /** Never seek beyond a recording's reported complete ten-second chunk. */
    fun targetPosition(durationMs: Long, rewindMs: Long?): Long {
        val edge = (durationMs.coerceAtLeast(0) / DURATION_QUANTUM_MS) * DURATION_QUANTUM_MS
        return if (rewindMs == null) 0 else (edge - rewindMs.coerceAtLeast(DURATION_QUANTUM_MS)).coerceAtLeast(0)
    }

    /** Twitch archives report their total; otherwise sum the listed segments. */
    fun mediaPlaylistDurationMs(body: String?): Long? {
        if (body?.trimStart()?.startsWith("#EXTM3U") != true) return null
        val seconds = Regex("""#EXT-X-TWITCH-TOTAL-SECS:([0-9.]+)""").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: Regex("""#EXTINF:([0-9.]+)""").findAll(body).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
        return (seconds * 1000).toLong().takeIf { it > 0 }
    }

    fun isPlayablePlaylist(body: String?, errorCode: Int?): Boolean = errorCode == null &&
        body?.trimStart()?.startsWith("#EXTM3U") == true &&
        (body.contains("#EXT-X-STREAM-INF:") || body.contains("#EXTINF:"))
}
