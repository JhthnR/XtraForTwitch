package com.github.andreyasadchy.xtra.ui.player

/** A rectangle in window pixels. */
data class FoldBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** A half-open hinge as reported by Window Manager, in window pixels. */
data class FoldHinge(val horizontal: Boolean, val bounds: FoldBounds)

/** How the maximized player splits video and chat at a half-open hinge. */
sealed interface PlayerFoldLayout {
    /** Hinge across the screen, like a laptop: video above it, chat below. */
    data class Tabletop(val videoHeight: Int, val chatHeight: Int) : PlayerFoldLayout

    /** Hinge down the screen, like a book: one pane on each side. */
    data class Book(val leftWidth: Int, val rightWidth: Int) : PlayerFoldLayout
}

object PlayerFoldLayoutPolicy {
    /** Smallest pane, in dp, that still fits the video controls or a usable chat. */
    const val MIN_PANE_DP = 160

    /** Chat height, in dp, kept above the keyboard while typing in tabletop posture. */
    const val MIN_CHAT_DP = 120

    /**
     * Splits [container] at [hinge], both in window pixels; pane sizes are container pixels.
     * Returns null when the hinge does not cross the whole container, for example in a
     * split-screen window, or when either side would be smaller than [minPanePx].
     */
    fun resolve(hinge: FoldHinge, container: FoldBounds, minPanePx: Int): PlayerFoldLayout? {
        val h = hinge.bounds
        if (container.right <= container.left || container.bottom <= container.top) return null
        return if (hinge.horizontal) {
            if (h.left > container.left || h.right < container.right) return null
            val videoHeight = h.top - container.top
            val chatHeight = container.bottom - h.bottom
            if (videoHeight < minPanePx || chatHeight < minPanePx) return null
            PlayerFoldLayout.Tabletop(videoHeight, chatHeight)
        } else {
            if (h.top > container.top || h.bottom < container.bottom) return null
            val leftWidth = h.left - container.left
            val rightWidth = container.right - h.right
            if (leftWidth < minPanePx || rightWidth < minPanePx) return null
            PlayerFoldLayout.Book(leftWidth, rightWidth)
        }
    }
}
