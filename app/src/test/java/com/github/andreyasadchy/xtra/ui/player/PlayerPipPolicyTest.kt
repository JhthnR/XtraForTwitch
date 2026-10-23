package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.graphics.Rect
import android.util.Rational
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class PlayerPipPolicyTest {
    private val eligible = PlayerPipPolicy.ChatEligibility(
        enabled = true, live = true, portrait = true, maximized = true,
        chatShown = true, chatReady = true, local = true, television = false,
        floating = false, splitAtHinge = false,
    )

    @Test fun `only opted in visible portrait live chat qualifies`() {
        assertTrue(eligible.canShow())
        for (state in listOf(
            eligible.copy(enabled = false), eligible.copy(live = false),
            eligible.copy(portrait = false), eligible.copy(maximized = false),
            eligible.copy(chatShown = false), eligible.copy(chatReady = false),
            eligible.copy(local = false), eligible.copy(television = true),
            eligible.copy(floating = true), eligible.copy(splitAtHinge = true),
        )) assertFalse(state.toString(), state.canShow())
    }

    @Test fun `chat request uses the top portrait slice in screen coordinates`() {
        val content = Rect(20, 64, 1100, 2400)
        val result = PlayerPipPolicy.presentation(Rect(20, 64, 1100, 671), content, true)!!
        assertEquals(Rational(9, 16), result.aspectRatio)
        assertEquals(Rect(20, 64, 1100, 1984), result.source)
        assertEquals(Rect(20, 64, 1100, 2400), content)
        assertTrue(result.chat)
    }

    @Test fun `chat hint never extends past a short resized window`() {
        val bounds = Rect(0, 32, 900, 1000)
        assertEquals(bounds, PlayerPipPolicy.presentation(Rect(), bounds, true)!!.source)
    }

    @Test fun `video only hint excludes chat and empty views do not issue requests`() {
        val video = Rect(0, 64, 1080, 671)
        val result = PlayerPipPolicy.presentation(video, Rect(0, 64, 1080, 2400), false)!!
        assertEquals(video, result.source)
        assertFalse(result.chat)
        assertNull(PlayerPipPolicy.presentation(Rect(), Rect(0, 0, 0, 900), true))
        assertNull(PlayerPipPolicy.presentation(Rect(), Rect(0, 0, 900, 1200), false))
    }

    @Test fun `video only PiP keeps sixteen by nine across wide sidebar and tall containers`() {
        for (video in listOf(Rect(0, 0, 2400, 1080), Rect(0, 0, 1600, 1080), Rect(0, 0, 600, 1000))) {
            assertEquals(Rational(16, 9), PlayerPipPolicy.presentation(video, video, false)!!.aspectRatio)
        }
    }
}
