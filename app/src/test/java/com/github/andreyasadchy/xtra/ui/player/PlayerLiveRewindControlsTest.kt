package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.PlayerLayoutBinding
import com.github.andreyasadchy.xtra.ui.UiTestRender
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLiveRewindControlsTest {
    private fun controls(fontScale: Float = 1f): PlayerLayoutBinding {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { this.fontScale = fontScale }
        val context = ContextThemeWrapper(app.createConfigurationContext(config), R.style.BaseDarkTheme)
        return PlayerLayoutBinding.inflate(LayoutInflater.from(context)).apply {
            root.visibility = View.VISIBLE
            root.alpha = 1f
            // This detached inflater has no AppCompat activity factory to apply app:tint.
            listOf(playPause, restart, seekLive, liveRewind, volume, audioCompressor,
                audioOnly, toggleChatInput, fullscreen, uptimeIcon, viewersIcon).forEach {
                it.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            listOf(restart, seekLive, volume, audioCompressor, audioOnly, toggleChatInput, fullscreen,
                uptimeLayout, uptimeIcon, viewersIcon).forEach { it.visibility = View.VISIBLE }
            uptimeTimer.text = "12:34:56"
            viewersText.text = "123.4K"
            listOf(position, duration, progressBar, rewind, fastForward).forEach { it.visibility = View.GONE }
        }
    }

    private fun layout(controls: PlayerLayoutBinding, width: Int, height: Int) {
        controls.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        controls.root.layout(0, 0, width, height)
    }

    @Test fun `portrait rewind remains clickable with optional buttons and large text in RTL too`() {
        for (scale in listOf(1f, 2f)) for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            val controls = controls(scale)
            controls.root.layoutDirection = direction
            var clicks = 0
            controls.bindLiveRewindButton(true, true) { clicks++ }
            layout(controls, 360, 203)
            assertTrue(controls.liveRewind.performClick())
            assertEquals(1, clicks)
            assertEquals(View.VISIBLE, controls.liveRewind.visibility)
            val left = controls.bottomLeftLayout
            val right = controls.bottomRightLayout
            assertTrue(left.right <= right.left || right.right <= left.left)
            assertTrue(left.bottom < controls.streamInfoLayout.top)
            assertTrue(right.bottom < controls.streamInfoLayout.top)
            assertTrue(controls.streamInfoLayout.left >= 0)
            assertTrue(controls.streamInfoLayout.right <= 360)
            assertTrue(controls.playPause.bottom + controls.playPause.translationY < left.top)
            assertTrue(controls.playPause.top + controls.playPause.translationY >= 0)
            UiTestRender.save(controls.root, "live-rewind-portrait-${scale}-${direction}")
        }
    }

    @Test fun `orientation and hiding restore normal row rules and remove the action`() {
        val controls = controls()
        controls.bindLiveRewindButton(true, true) {}
        layout(controls, 360, 203)
        controls.bindLiveRewindButton(true, false) {}
        layout(controls, 800, 450)
        assertEquals(0f, controls.playPause.translationY)
        assertEquals(controls.bottomLayout.id, (controls.bottomLeftLayout.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.ABOVE))
        UiTestRender.save(controls.root, "live-rewind-landscape")
        controls.bindLiveRewindButton(false, true) { fail("Hidden action must not execute") }
        layout(controls, 360, 203)
        assertEquals(View.GONE, controls.liveRewind.visibility)
        assertFalse(controls.liveRewind.hasOnClickListeners())
    }

    @Test fun `new orientation wins if portrait layout has not run yet`() {
        val controls = controls()
        controls.bindLiveRewindButton(true, true) {}
        controls.bindLiveRewindButton(true, false) {}
        layout(controls, 800, 450)
        assertEquals(0f, controls.playPause.translationY)
    }
}
