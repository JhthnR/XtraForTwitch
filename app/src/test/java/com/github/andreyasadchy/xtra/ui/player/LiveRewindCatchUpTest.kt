package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRewindCatchUpTest {

    @Test fun `fast recording near edge resets to live speed`() {
        assertTrue(
            LiveRewind.shouldResetSpeedAtLiveEdge(
                hasLiveRewindOrigin = true,
                currentSpeed = 4f,
                positionMs = 596_000L,
                durationMs = 600_000L,
            )
        )
    }

    @Test fun `faster catch-up resets earlier so it lands before the edge`() {
        assertEquals(LiveRewind.LIVE_EDGE_SLACK_MS, LiveRewind.edgeSlackMs(1.5f))
        assertEquals(12_000L, LiveRewind.edgeSlackMs(8f))
        // 12 s of media left is 1.5 s of real time at 8x, but 3 s at 4x.
        assertTrue(LiveRewind.shouldResetSpeedAtLiveEdge(true, 8f, 588_000L, 600_000L))
        assertFalse(LiveRewind.shouldResetSpeedAtLiveEdge(true, 4f, 588_000L, 600_000L))
    }

    @Test fun `unknown live duration never resets`() {
        assertFalse(LiveRewind.shouldResetSpeedAtLiveEdge(true, 4f, 10_000L, -9_223_372_036_854_775_807L))
    }

    @Test fun `normal speed near edge does not reset`() {
        assertFalse(
            LiveRewind.shouldResetSpeedAtLiveEdge(
                hasLiveRewindOrigin = true,
                currentSpeed = 1f,
                positionMs = 599_000L,
                durationMs = 600_000L,
            )
        )
    }

    @Test fun `fast recording away from edge does not reset`() {
        assertFalse(
            LiveRewind.shouldResetSpeedAtLiveEdge(
                hasLiveRewindOrigin = true,
                currentSpeed = 4f,
                positionMs = 500_000L,
                durationMs = 600_000L,
            )
        )
    }

    @Test fun `ordinary video never resets`() {
        assertFalse(
            LiveRewind.shouldResetSpeedAtLiveEdge(
                hasLiveRewindOrigin = false,
                currentSpeed = 4f,
                positionMs = 599_000L,
                durationMs = 600_000L,
            )
        )
    }

    @Test fun `fullscreen button shows only for maximized landscape live`() {
        assertTrue(
            LiveRewind.shouldShowFullscreenLiveRewind(
                videoType = PlayerFragment.STREAM,
                hasLiveRewindOrigin = false,
                isMaximized = true,
                isPortrait = false,
            )
        )
        assertFalse(
            LiveRewind.shouldShowFullscreenLiveRewind(
                videoType = PlayerFragment.STREAM,
                hasLiveRewindOrigin = false,
                isMaximized = true,
                isPortrait = true,
            )
        )
        assertFalse(
            LiveRewind.shouldShowFullscreenLiveRewind(
                videoType = PlayerFragment.STREAM,
                hasLiveRewindOrigin = false,
                isMaximized = false,
                isPortrait = false,
            )
        )
        assertFalse(
            LiveRewind.shouldShowFullscreenLiveRewind(
                videoType = PlayerFragment.VIDEO,
                hasLiveRewindOrigin = false,
                isMaximized = true,
                isPortrait = false,
            )
        )
        assertFalse(
            LiveRewind.shouldShowFullscreenLiveRewind(
                videoType = PlayerFragment.STREAM,
                hasLiveRewindOrigin = true,
                isMaximized = true,
                isPortrait = false,
            )
        )
    }
}
