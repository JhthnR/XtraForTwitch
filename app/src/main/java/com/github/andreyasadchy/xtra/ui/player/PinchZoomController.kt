package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.abs
import kotlin.math.ln

/**
 * Continuous, centered pinch zoom between Fit and Fill for API 24+ surfaces.
 * Independent of Android views, like [PinchDisplayModeController].
 *
 * Zoom is relative to Fit: 1 is Fit and the Fill-to-Fit ratio is Fill. The
 * picture follows the fingers (start zoom times cumulative span scale).
 * Within [DETENT_ZOOM] of either end it snaps to that end, and pinching past
 * an end resists elastically, then settles back on release. Releasing between
 * the detents keeps that zoom.
 */
class PinchZoomController {

    companion object {
        /** Each end captures zooms within this factor of it (5%). */
        const val DETENT_ZOOM = 1.05f
        /** Smallest free band that must remain between the two detents. */
        private const val MIN_FREE_BAND_ZOOM = 1.02f
        /** Overshoot past an end at which elastic resistance saturates. */
        private const val ELASTIC_SATURATION_ZOOM = 1.12f
        private const val UNCHANGED_EPSILON = 0.001f

        /**
         * Whether a viewport whose Fill-to-Fit ratio is [maxZoom] leaves a
         * usable band between the detents (ratio of about 1.12 or more).
         * Otherwise Fit and Fill stay a toggle.
         */
        fun supports(maxZoom: Float): Boolean {
            return maxZoom.isFinite() && ln(maxZoom) >= 2 * ln(DETENT_ZOOM) + ln(MIN_FREE_BAND_ZOOM)
        }
    }

    enum class Target { FIT, ZOOM, FILL }

    /**
     * One rendered step. [zoom] is what a release would keep (1 at Fit, the
     * maximum at Fill); [displayZoom] is what to draw now, including elastic
     * overshoot. [fraction] runs 0..1 from Fit to Fill in log scale.
     * [reachedEnd] is true on the step that enters Fit or Fill.
     */
    data class Frame(
        val target: Target,
        val zoom: Float,
        val displayZoom: Float,
        val fraction: Float,
        val reachedEnd: Boolean,
    )

    /**
     * Final state: Fill, or Fit with [zoom] (1 for plain Fit). [target] and
     * [fraction] describe it like a [Frame].
     */
    data class Result(
        val mode: PlayerDisplayMode,
        val zoom: Float,
        val target: Target,
        val fraction: Float,
        val changed: Boolean,
    )

    var maxZoom = 1f
        private set

    private var startMode = PlayerDisplayMode.FIT
    private var startStoredZoom = 1f
    private var startZoom = 1f
    private var lastTarget = Target.FIT
    private var current = frameFor(1f)
    private var startFrame = current

    /**
     * Starts a pinch from [mode] (Fit or Fill) and the stored Fit zoom.
     * [maxZoom] is the current Fill-to-Fit ratio; see [supports].
     */
    fun begin(mode: PlayerDisplayMode, zoom: Float, maxZoom: Float) {
        this.maxZoom = maxZoom.coerceAtLeast(1f)
        startMode = mode
        startStoredZoom = zoom
        startZoom = if (mode == PlayerDisplayMode.FILL) this.maxZoom else zoom.coerceIn(1f, this.maxZoom)
        current = frameFor(startZoom)
        startFrame = current
        lastTarget = current.target
    }

    /** [cumulativeScale] is the span scale relative to pinch start (1 = neutral). */
    fun update(cumulativeScale: Float): Frame {
        val frame = frameFor(startZoom * cumulativeScale)
        val reachedEnd = frame.target != Target.ZOOM && frame.target != lastTarget
        lastTarget = frame.target
        current = frame.copy(reachedEnd = reachedEnd)
        return current
    }

    /** Keeps the current Fit, Fill or in-between zoom. */
    fun release(): Result {
        val mode = if (current.target == Target.FILL) PlayerDisplayMode.FILL else PlayerDisplayMode.FIT
        val zoom = if (current.target == Target.ZOOM) current.zoom else 1f
        val changed = mode != startMode ||
            abs(zoom - (if (startMode == PlayerDisplayMode.FIT) startStoredZoom else 1f)) > UNCHANGED_EPSILON
        return Result(mode, zoom, current.target, current.fraction, changed)
    }

    /** Restores the state the pinch started from. */
    fun cancel(): Result = Result(startMode, startStoredZoom, startFrame.target, startFrame.fraction, changed = false)

    private fun frameFor(rawZoom: Float): Frame {
        val logMax = ln(maxZoom)
        val logDetent = ln(DETENT_ZOOM)
        val logZoom = ln(rawZoom.coerceAtLeast(0.0001f))
        return when {
            logZoom <= logDetent || logMax <= 0f -> Frame(
                target = Target.FIT,
                zoom = 1f,
                displayZoom = if (logZoom < 0f) 1f - elasticDelta(-logZoom) else 1f,
                fraction = 0f,
                reachedEnd = false,
            )
            logZoom >= logMax - logDetent -> Frame(
                target = Target.FILL,
                zoom = maxZoom,
                displayZoom = if (logZoom > logMax) maxZoom * (1f + elasticDelta(logZoom - logMax)) else maxZoom,
                fraction = 1f,
                reachedEnd = false,
            )
            else -> Frame(
                target = Target.ZOOM,
                zoom = rawZoom,
                displayZoom = rawZoom,
                fraction = (logZoom / logMax).coerceIn(0f, 1f),
                reachedEnd = false,
            )
        }
    }

    /**
     * Restrained endpoint deformation: the same quadratic ease-out and 5%
     * ceiling as the Fit/Fill toggle's elastic feedback.
     */
    private fun elasticDelta(logOvershoot: Float): Float {
        val normalized = (logOvershoot / ln(ELASTIC_SATURATION_ZOOM)).coerceIn(0f, 1f)
        val remaining = 1f - normalized
        return PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA * (1f - remaining * remaining)
    }
}
