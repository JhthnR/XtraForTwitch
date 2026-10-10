package com.github.andreyasadchy.xtra.ui.player

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
