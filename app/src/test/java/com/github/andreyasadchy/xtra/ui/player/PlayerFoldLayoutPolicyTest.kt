package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerFoldLayoutPolicyTest {

    // Pixel 9 Pro Fold inner screen: 2076 x 2152, hinge at x = 1038.
    private val window = FoldBounds(0, 0, 2076, 2152)
    private val minPane = 420

    @Test
    fun `tabletop puts video above the hinge and chat below it`() {
        val hinge = FoldHinge(horizontal = true, bounds = FoldBounds(0, 1076, 2076, 1076))
        // Status bar padding in portrait moves the container down by 120 px.
        val container = FoldBounds(0, 120, 2076, 2152)

        assertEquals(
            PlayerFoldLayout.Tabletop(videoHeight = 956, chatHeight = 1076),
            PlayerFoldLayoutPolicy.resolve(hinge, container, minPane),
        )
    }

    @Test
    fun `book posture splits the panes at the hinge`() {
        val hinge = FoldHinge(horizontal = false, bounds = FoldBounds(1038, 0, 1038, 2152))

        assertEquals(
            PlayerFoldLayout.Book(leftWidth = 1038, rightWidth = 1038),
            PlayerFoldLayoutPolicy.resolve(hinge, window, minPane),
        )
    }

    @Test
    fun `a physical hinge gap belongs to neither pane`() {
        val hinge = FoldHinge(horizontal = false, bounds = FoldBounds(1000, 0, 1076, 2152))

        assertEquals(
            PlayerFoldLayout.Book(leftWidth = 1000, rightWidth = 1000),
            PlayerFoldLayoutPolicy.resolve(hinge, window, minPane),
        )
    }

    @Test
    fun `side cutout padding narrows the book panes`() {
        val hinge = FoldHinge(horizontal = false, bounds = FoldBounds(1038, 0, 1038, 2152))
        val container = FoldBounds(100, 0, 2076, 2152)

        assertEquals(
            PlayerFoldLayout.Book(leftWidth = 938, rightWidth = 1038),
            PlayerFoldLayoutPolicy.resolve(hinge, container, minPane),
        )
    }

    @Test
    fun `a hinge that does not cross the whole container is ignored`() {
        // Split-screen window on one side of a horizontal hinge's span.
        val hinge = FoldHinge(horizontal = true, bounds = FoldBounds(0, 1076, 1000, 1076))

        assertNull(PlayerFoldLayoutPolicy.resolve(hinge, window, minPane))
    }

    @Test
    fun `a pane smaller than the minimum keeps the normal layout`() {
        val hinge = FoldHinge(horizontal = true, bounds = FoldBounds(0, 300, 2076, 300))

        assertNull(PlayerFoldLayoutPolicy.resolve(hinge, window, minPane))
    }

    @Test
    fun `an unmeasured container keeps the normal layout`() {
        val hinge = FoldHinge(horizontal = true, bounds = FoldBounds(0, 1076, 2076, 1076))

        assertNull(PlayerFoldLayoutPolicy.resolve(hinge, FoldBounds(0, 0, 0, 0), minPane))
    }

    @Test fun `a keyboard resized tabletop window keeps the video pane`() {
        val hinge = FoldHinge(horizontal = true, bounds = FoldBounds(0, 1076, 2076, 1076))
        assertEquals(PlayerFoldLayout.Tabletop(1076, 44),
            PlayerFoldLayoutPolicy.resolve(hinge, FoldBounds(0, 0, 2076, 1120), minPane, minChatPanePx = 0))
        assertNull(PlayerFoldLayoutPolicy.resolve(hinge, FoldBounds(0, 0, 2076, 1000), minPane, minChatPanePx = 0))
    }
}
