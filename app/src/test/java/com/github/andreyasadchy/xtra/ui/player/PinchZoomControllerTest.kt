package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.ui.player.PinchZoomController.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class PinchZoomControllerTest {

    /** 16:9 video on a near-square foldable inner screen in landscape. */
    private val foldable = PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2352, 2172)

    private fun controller(mode: PlayerDisplayMode = PlayerDisplayMode.FIT, zoom: Float = 1f, max: Float = foldable) =
        PinchZoomController().apply { begin(mode, zoom, max) }

    @Test
    fun `free zoom needs a band between the two detents`() {
        assertFalse(PinchZoomController.supports(1f))
        // 16:9 video on a 16:10 tablet: only a toggle.
        assertFalse(PinchZoomController.supports(PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2560, 1600)))
        assertFalse(PinchZoomController.supports(Float.NaN))
        // 20:9 phone and the foldable inner screen.
        assertTrue(PinchZoomController.supports(PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2400, 1080)))
        assertTrue(PinchZoomController.supports(foldable))
    }

    @Test
    fun `zoom follows the fingers and is kept on release`() {
        val pinch = controller()
        val frame = pinch.update(1.3f)
        assertEquals(Target.ZOOM, frame.target)
        assertEquals(1.3f, frame.zoom, 0.0001f)
        assertEquals(1.3f, frame.displayZoom, 0.0001f)
        assertEquals(ln(1.3f) / ln(foldable), frame.fraction, 0.0001f)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(1.3f, result.zoom, 0.0001f)
        assertEquals(Target.ZOOM, result.target)
        assertTrue(result.changed)
    }

    @Test
    fun `near Fit snaps to plain Fit and is unchanged`() {
        val pinch = controller()
        val frame = pinch.update(1.04f)
        assertEquals(Target.FIT, frame.target)
        assertEquals(1f, frame.displayZoom, 0f)
        assertFalse(frame.reachedEnd)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(1f, result.zoom, 0f)
        assertFalse(result.changed)
    }

    @Test
    fun `near Fill snaps to Fill once with a single end tick`() {
        val pinch = controller()
        val entered = pinch.update(foldable / 1.04f)
        assertEquals(Target.FILL, entered.target)
        assertEquals(foldable, entered.displayZoom, 0.0001f)
        assertEquals(1f, entered.fraction, 0f)
        assertTrue(entered.reachedEnd)
        assertFalse(pinch.update(foldable).reachedEnd)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FILL, result.mode)
        assertEquals(1f, result.zoom, 0f)
        assertTrue(result.changed)
    }

    @Test
    fun `returning to Fit ticks but starting there does not`() {
        val pinch = controller()
        assertFalse(pinch.update(0.98f).reachedEnd)
        assertFalse(pinch.update(1.3f).reachedEnd)
        assertTrue(pinch.update(1.02f).reachedEnd)
    }

    @Test
    fun `pinching past either end resists up to five percent`() {
        val belowFit = controller().update(0.5f)
        assertEquals(Target.FIT, belowFit.target)
        assertTrue(belowFit.displayZoom < 1f)
        assertEquals(1f - PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA, belowFit.displayZoom, 0.0001f)

        val pinch = controller()
        val slightlyPastFill = pinch.update(foldable * 1.03f)
        val farPastFill = pinch.update(foldable * 3f)
        assertEquals(Target.FILL, farPastFill.target)
        assertEquals(foldable, farPastFill.zoom, 0.0001f)
        assertTrue(slightlyPastFill.displayZoom > foldable)
        assertTrue(farPastFill.displayZoom > slightlyPastFill.displayZoom)
        assertEquals(foldable * (1f + PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA), farPastFill.displayZoom, 0.0001f)
        assertEquals(PlayerDisplayMode.FILL, pinch.release().mode)
    }

    @Test
    fun `pinching in from Fill keeps an intermediate zoom`() {
        val pinch = controller(mode = PlayerDisplayMode.FILL)
        val frame = pinch.update(0.8f)
        assertEquals(Target.ZOOM, frame.target)
        assertEquals(foldable * 0.8f, frame.zoom, 0.0001f)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(foldable * 0.8f, result.zoom, 0.0001f)
        assertTrue(result.changed)
    }

    @Test
    fun `a kept zoom resumes where it was`() {
        val pinch = controller(zoom = 1.3f)
        val frame = pinch.update(1f)
        assertEquals(Target.ZOOM, frame.target)
        assertEquals(1.3f, frame.displayZoom, 0.0001f)
        assertFalse(frame.reachedEnd)
        assertFalse(pinch.release().changed)
    }

    @Test
    fun `a kept zoom beyond a narrower screen's Fill starts at Fill`() {
        val phone = PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2400, 1080)
        val pinch = controller(zoom = 1.5f, max = phone)
        val frame = pinch.update(1.01f)
        assertEquals(Target.FILL, frame.target)
        assertEquals(phone, frame.zoom, 0.0001f)
        assertFalse(frame.reachedEnd)
    }

    @Test
    fun `cancel restores the starting state`() {
        val pinch = controller(zoom = 1.3f)
        pinch.update(1.2f)
        val result = pinch.cancel()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(1.3f, result.zoom, 0f)
        assertEquals(Target.ZOOM, result.target)
        assertFalse(result.changed)
    }

    @Test
    fun `displayed zoom never moves against the fingers`() {
        val pinch = controller()
        var previous = 0f
        var scale = 0.5f
        while (scale <= 3f) {
            val displayed = pinch.update(scale).displayZoom
            assertTrue("$scale: $displayed < $previous", displayed >= previous)
            previous = displayed
            scale += 0.01f
        }
    }
}
