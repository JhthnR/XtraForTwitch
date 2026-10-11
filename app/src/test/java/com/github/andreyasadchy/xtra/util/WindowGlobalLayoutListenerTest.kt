package com.github.andreyasadchy.xtra.util

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class WindowGlobalLayoutListenerTest {

    private fun attachedRoot(): FrameLayout {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        return FrameLayout(activity).also(activity::setContentView)
    }

    @Test fun `listener leaves the window when its view is removed`() {
        val root = attachedRoot()
        val view = View(root.context).also(root::addView)
        var calls = 0
        view.addWindowGlobalLayoutListener { calls++ }
        root.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals(1, calls)

        root.removeView(view)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals("a removed view must not keep its listener on the window", 1, calls)

        root.addView(view)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals(2, calls)
    }

    @Test fun `listener added before attach runs once attached`() {
        val root = attachedRoot()
        val view = View(root.context)
        var calls = 0
        view.addWindowGlobalLayoutListener { calls++ }
        root.addView(view)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals(1, calls)
    }
}
