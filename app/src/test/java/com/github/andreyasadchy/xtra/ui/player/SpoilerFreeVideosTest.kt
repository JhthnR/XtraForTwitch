package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoilerFreeVideosTest {

    @Test fun `player hides progress only for online and downloaded VODs when enabled`() {
        assertTrue(SpoilerFreeVideos.hidePlayerProgress(true, PlayerFragment.VIDEO))
        assertTrue(SpoilerFreeVideos.hidePlayerProgress(true, PlayerFragment.OFFLINE_VIDEO))
        assertFalse(SpoilerFreeVideos.hidePlayerProgress(true, PlayerFragment.STREAM))
        assertFalse(SpoilerFreeVideos.hidePlayerProgress(true, PlayerFragment.CLIP))
        assertFalse(SpoilerFreeVideos.hidePlayerProgress(true, null))
    }

    @Test fun `seek pill drops the length only when hidden`() {
        assertEquals("1:23:45 / 3:00:00", SpoilerFreeVideos.seekLabel("1:23:45", "3:00:00", false))
        assertEquals("1:23:45", SpoilerFreeVideos.seekLabel("1:23:45", "3:00:00", true))
    }

    @Test fun `player shows progress when disabled regardless of type`() {
        assertFalse(SpoilerFreeVideos.hidePlayerProgress(false, PlayerFragment.VIDEO))
        assertFalse(SpoilerFreeVideos.hidePlayerProgress(false, PlayerFragment.STREAM))
    }

    @Test fun `list spoilers follow the preference`() {
        assertTrue(SpoilerFreeVideos.hideListSpoilers(true))
        assertFalse(SpoilerFreeVideos.hideListSpoilers(false))
    }
}
