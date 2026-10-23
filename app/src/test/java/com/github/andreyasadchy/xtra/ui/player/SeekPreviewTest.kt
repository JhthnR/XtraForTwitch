package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import coil3.ImageLoader
import coil3.asImage
import coil3.request.Disposable
import coil3.request.ImageRequest
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.PlayerLayoutBinding
import com.github.andreyasadchy.xtra.model.VideoStoryboard
import com.github.andreyasadchy.xtra.ui.UiTestRender
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeekPreviewTest {
    private val requests = mutableListOf<ImageRequest>()
    private val disposable = mock<Disposable>()
    private val loader = mock<ImageLoader>().also {
        whenever(it.enqueue(any())).thenAnswer { call ->
            requests += call.getArgument<ImageRequest>(0)
            disposable
        }
    }
    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
    private val controls = PlayerLayoutBinding.inflate(LayoutInflater.from(context))
    private val preview = SeekPreview(controls.seekPreviewLayout, controls.seekPreviewFrame, controls.seekPreviewTime, loader)
    private val board = requireNotNull(VideoStoryboard.from("https://example.com/info.json", listOf(
        VideoStoryboard.Info(4, 160, 90, 2, 1, 10.0, images = listOf("a.jpg", "b.jpg")),
    )))

    private fun layout(width: Int, height: Int) {
        controls.root.visibility = View.VISIBLE
        controls.root.alpha = 1f
        controls.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        controls.root.layout(0, 0, width, height)
    }

    private fun sheet(): Bitmap = Bitmap.createBitmap(320, 90, Bitmap.Config.RGB_565).apply {
        eraseColor(Color.RED)
        val canvas = Canvas(this)
        val paint = android.graphics.Paint().apply { color = Color.BLUE }
        canvas.drawRect(160f, 0f, 320f, 90f, paint)
    }

    @Test fun `drag reuses sheet and draws only the chosen cell`() {
        layout(360, 203)
        preview.storyboard = board
        preview.show(controls.progressBar, 0, 40_000)
        requests.single().target!!.onSuccess(sheet().asImage())
        preview.show(controls.progressBar, 10_000, 40_000)
        assertEquals(1, requests.size)
        assertEquals("00:10", controls.seekPreviewTime.text.toString())
        val output = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        controls.seekPreviewFrame.draw(Canvas(output))
        assertEquals(Color.BLUE, output.getPixel(80, 45))
        layout(360, 203)
        assertTrue(controls.seekPreviewTime.width > 0)
        UiTestRender.save(controls.root, "seek-preview-phone")
    }

    @Test fun `sheet changes clear the old image and ignore stale completions`() {
        layout(800, 450)
        preview.storyboard = board
        preview.show(controls.progressBar, 0, 40_000)
        val oldRequest = requests.single()
        preview.show(controls.progressBar, 20_000, 40_000)
        oldRequest.target!!.onSuccess(sheet().asImage())
        val output = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        controls.seekPreviewFrame.draw(Canvas(output))
        assertEquals(Color.BLACK, output.getPixel(80, 45))
        assertEquals(2, requests.size)
        requests.last().target!!.onSuccess(sheet().asImage())
        layout(800, 450)
        UiTestRender.save(controls.root, "seek-preview-wide")
    }

    @Test fun `hide cancels image work and an old callback cannot fill a new drag of same URL`() {
        layout(360, 203)
        preview.storyboard = board
        preview.show(controls.progressBar, 0, 40_000)
        val old = requests.single()
        preview.hide()
        verify(disposable).dispose()
        assertEquals(View.INVISIBLE, controls.seekPreviewLayout.visibility)
        preview.show(controls.progressBar, 0, 40_000)
        old.target!!.onSuccess(sheet().asImage())
        val output = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        controls.seekPreviewFrame.draw(Canvas(output))
        assertEquals(Color.BLACK, output.getPixel(80, 45))
        preview.release()
        assertNull(preview.storyboard)
        assertEquals(View.INVISIBLE, controls.seekPreviewLayout.visibility)
    }

    @Test fun `preview stays within compact wide and RTL controls at both ends`() {
        preview.storyboard = board
        for ((width, height) in listOf(360 to 203, 800 to 450)) {
            for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                controls.root.layoutDirection = direction
                layout(width, height)
                for (position in listOf(0L, 40_000L)) {
                    preview.show(controls.progressBar, position, 40_000)
                    val view = controls.seekPreviewLayout
                    val left = view.left + view.translationX
                    assertTrue(left >= 8)
                    assertTrue(left + view.width <= width - 8)
                    assertTrue(view.top >= 0)
                    assertTrue(view.bottom <= controls.bottomLayout.top)
                }
            }
        }
        preview.show(controls.progressBar, 0, 0)
        assertEquals(View.INVISIBLE, controls.seekPreviewLayout.visibility)
    }
}
