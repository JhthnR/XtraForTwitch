package com.github.andreyasadchy.xtra.ui.player

/**
 * Presentation rules for the opt-in "spoiler-free VODs" preference (issue #34).
 *
 * A viewer watching esports VODs does not want a video's length or watched
 * progress to reveal how many games were played. When enabled, the seek bar,
 * position and duration are hidden during VOD and downloaded-VOD playback, the
 * swipe-to-seek pill shows only the position, duration plus watched-progress
 * overlays are hidden on VOD, Saved and Downloads thumbnails, and the download
 * and games dialogs leave out the length and chapter times.
 * Default is off, so nothing changes unless the viewer opts in. Live streams
 * and clips are unaffected.
 *
 * Pure and independent of Android views so it is unit-testable.
 */
object SpoilerFreeVideos {

    /**
     * Whether the player should hide the seek bar, position and duration.
     * Online and downloaded VODs are affected; live streams never show these
     * anyway and clips are out of scope for this preference.
     */
    fun hidePlayerProgress(spoilerFreeEnabled: Boolean, videoType: String?): Boolean {
        return spoilerFreeEnabled && (videoType == PlayerFragment.VIDEO || videoType == PlayerFragment.OFFLINE_VIDEO)
    }

    /** Swipe-to-seek label: `position / length`, or just the position when the length is hidden. */
    fun seekLabel(position: String, length: String, hideLength: Boolean): String {
        return if (hideLength) position else "$position / $length"
    }

    /** Whether thumbnail lists should hide the duration overlay and watched bar. */
    fun hideListSpoilers(spoilerFreeEnabled: Boolean): Boolean = spoilerFreeEnabled
}
