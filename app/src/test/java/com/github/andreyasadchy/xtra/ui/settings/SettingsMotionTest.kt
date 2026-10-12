package com.github.andreyasadchy.xtra.ui.settings

import android.animation.ValueAnimator
import android.app.Application
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [34], qualifiers = "mdpi")
class SettingsMotionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun frames(forward: Boolean, appearing: Boolean): List<Pair<Float, Float>> {
        val root = FrameLayout(context)
        val view = View(context).also(root::addView)
        val transition = SettingsAxisTransition(forward)
        val animator = (if (appearing) transition.onAppear(root, view, null, null) else transition.onDisappear(root, view, null, null)) as ValueAnimator
        return listOf(0f, 0.35f, 1f).map { fraction ->
            animator.setCurrentFraction(fraction)
            view.alpha to view.translationX
        }
    }

    @Test fun `transition supports seeking so predictive back can scrub it`() {
        assertTrue(SettingsAxisTransition(forward = false).isSeekingSupported)
    }

    @Test fun `screens fade through instead of overlapping`() {
        val outgoing = frames(forward = false, appearing = false)
        val incoming = frames(forward = false, appearing = true)
        assertEquals(listOf(1f, 0f, 0f), outgoing.map { it.first })
        assertEquals(listOf(0f, 0f, 1f), incoming.map { it.first })
    }

    @Test fun `forward slides in from the end and back slides in from the start`() {
        assertEquals(30f, frames(forward = true, appearing = true).first().second, 0.01f)
        assertEquals(-30f, frames(forward = false, appearing = true).first().second, 0.01f)
        assertEquals(30f, frames(forward = false, appearing = false).last().second, 0.01f)
        assertEquals(0f, frames(forward = true, appearing = true).last().second, 0.01f)
    }
}
