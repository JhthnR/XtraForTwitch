package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.media3.ui.AspectRatioFrameLayout
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/** Drives the real fragment pinch path on a near-square landscape player (Fill is about 1.64x Fit). */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28], qualifiers = "land")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerPinchZoomTest {
    private lateinit var activity: ActivityController<AppCompatActivity>
    private lateinit var binding: FragmentPlayerBinding
    private lateinit var fragment: PlayerFragment
    private val frame get() = binding.aspectRatioFrameLayout

    @Before fun setup() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup()
        val context = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        context.prefs().edit { clear() }
        binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        activity.get().setContentView(binding.root)
        resizePlayer(1083, 1000)
        fragment = Mockito.mock(PlayerFragment::class.java,
            Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))
        Mockito.doReturn(context).`when`(fragment).context
        Mockito.doReturn(android.os.Bundle()).`when`(fragment).arguments
        ReflectionHelpers.setField(fragment, "_binding", binding)
        ReflectionHelpers.setField(fragment, "displayModeStore",
            PlayerDisplayModeStore(SharedPreferencesDisplayModeStorage(context.prefs())))
        ReflectionHelpers.setField(fragment, "videoAspectRatio", 16f / 9f)
        call("finalizePinchSurface")
        idle()
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
    }

    @Test fun `released zoom stays centered and is saved with fit`() {
        pinch(1.3f)
        assertEquals(PlayerDisplayMode.FIT, fragment.getCurrentDisplayMode())
        assertEquals(1.3f, fragment.getDisplayZoom(), 0.001f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, frame.resizeMode)
        assertEquals(1.3f, frame.scaleX, 0.001f)
        assertEquals(1.3f, frame.scaleY, 0.001f)
        // No explicit pivot: views scale about their center.
        assertFalse(frame.isPivotSet)
        val prefs = fragment.requireContext().prefs()
        assertEquals("fit", prefs.getString(C.PLAYER_DISPLAY_MODE_LANDSCAPE, null))
        assertEquals(1.3f, prefs.getFloat(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE, 0f), 0.001f)
    }

    @Test fun `pinching to fill swaps to the fill renderer at unit scale`() {
        pinch(1.7f)
        assertEquals(PlayerDisplayMode.FILL, fragment.getCurrentDisplayMode())
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, frame.resizeMode)
        assertEquals(1f, frame.scaleX, 0.001f)
        assertFalse(fragment.requireContext().prefs().contains(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE))
    }

    @Test fun `pinching in from fill keeps a zoom in the fit renderer`() {
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(0.8f)
        val expected = PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 1083, 1000) * 0.8f
        assertEquals(PlayerDisplayMode.FIT, fragment.getCurrentDisplayMode())
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, frame.resizeMode)
        assertEquals(expected, frame.scaleX, 0.001f)
    }

    @Test fun `kept zoom is clamped to fill and dropped where the shape leaves no band`() {
        pinch(1.5f)
        // A 16:9.6 area: Fill is only about 1.11x Fit, so the zoom draws plain Fit.
        resizePlayer(1600, 1000)
        call("refreshDisplayZoom")
        assertEquals(1f, frame.scaleX, 0.001f)
        // A 20:9 phone area: Fill is 1.25x Fit, below the kept 1.5x.
        resizePlayer(2222, 1000)
        call("refreshDisplayZoom")
        assertEquals(1.25f, frame.scaleX, 0.01f)
        resizePlayer(1083, 1000)
        call("refreshDisplayZoom")
        assertEquals(1.5f, frame.scaleX, 0.001f)
        assertEquals(1.5f, fragment.getDisplayZoom(), 0.001f)
    }

    @Test fun `choosing a mode clears the zoom and portrait draws plain fit`() {
        pinch(1.3f)
        fragment.selectDisplayMode(PlayerDisplayMode.FIT)
        idle()
        assertEquals(1f, fragment.getDisplayZoom(), 0f)
        assertEquals(1f, frame.scaleX, 0.001f)
        assertFalse(fragment.requireContext().prefs().contains(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE))

        pinch(1.3f)
        ReflectionHelpers.setField(fragment, "isPortrait", true)
        call("finalizePinchSurface")
        assertEquals(1f, frame.scaleX, 0.001f)
        assertEquals(1.3f, fragment.getDisplayZoom(), 0.001f)
    }

    @Test fun `cancelled pinch returns to the kept zoom`() {
        pinch(1.3f)
        pinch(1.2f, cancelled = true)
        assertEquals(1.3f, frame.scaleX, 0.001f)
        assertEquals(1.3f, fragment.getDisplayZoom(), 0.001f)
    }

    private fun pinch(scale: Float, cancelled: Boolean = false) {
        ReflectionHelpers.setField(fragment, "pinchPointerId1", 0)
        ReflectionHelpers.setField(fragment, "pinchPointerId2", 1)
        ReflectionHelpers.setField(fragment, "pinchAnchorSpan", 100f)
        twoFingers(100f) { event ->
            PlayerFragment::class.java.getDeclaredMethod(
                "beginPinch", Boolean::class.javaPrimitiveType, MotionEvent::class.java,
            ).apply { isAccessible = true }.invoke(fragment, false, event)
        }
        twoFingers(100f * scale) { event ->
            PlayerFragment::class.java.getDeclaredMethod("updatePinch", MotionEvent::class.java)
                .apply { isAccessible = true }.invoke(fragment, event)
        }
        PlayerFragment::class.java.getDeclaredMethod("finishPinch", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(fragment, cancelled)
        idle()
    }

    private fun twoFingers(span: Float, block: (MotionEvent) -> Unit) {
        val properties = Array(2) { id -> MotionEvent.PointerProperties().apply { this.id = id } }
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = 500f; y = 500f },
            MotionEvent.PointerCoords().apply { x = 500f + span; y = 500f },
        )
        val event = MotionEvent.obtain(0, 10, MotionEvent.ACTION_MOVE, 2, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        try {
            block(event)
        } finally {
            event.recycle()
        }
    }

    private fun call(name: String) {
        PlayerFragment::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(fragment)
    }

    private fun resizePlayer(width: Int, height: Int) {
        binding.playerLayout.layoutParams = FrameLayout.LayoutParams(width, height)
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }
}
