package com.github.andreyasadchy.xtra.ui.player

import android.graphics.Bitmap
import android.text.format.DateUtils
import android.view.View
import android.widget.TextView
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.allowHardware
import coil3.request.target
import coil3.size.Size
import com.github.andreyasadchy.xtra.model.VideoStoryboard
import com.github.andreyasadchy.xtra.ui.view.StoryboardFrameView

/**
 * Thumbnail shown above the VoD seek bar while it is dragged. [container] is a direct
 * child of the player controls and only moves horizontally.
 */
class SeekPreview(
    private val container: View,
    private val frameView: StoryboardFrameView,
    private val timeView: TextView,
    private val imageLoader: ImageLoader = container.context.imageLoader,
) {

    var storyboard: VideoStoryboard? = null
        set(value) {
            if (field === value) return
            field = value
            clearSheet()
            hide()
        }

    private val density = container.resources.displayMetrics.density
    private val location = IntArray(2)
    private var sheetUrl: String? = null
    private var sheet: Bitmap? = null
    private var frame: VideoStoryboard.Frame? = null
    private var request: Disposable? = null
    private var requestGeneration = 0

    /** Shows the frame at [position] over its place on [timeBar]. Does nothing without a storyboard. */
    fun show(timeBar: View, position: Long, duration: Long) {
        val frame = storyboard?.takeIf { duration > 0 }?.frameAt(position) ?: run {
            hide()
            return
        }
        this.frame = frame
        val sheet = sheet
        if (frame.imageUrl != sheetUrl) {
            load(frame.imageUrl)
        } else if (sheet != null) {
            frameView.setFrame(sheet, frame.left, frame.top, frame.width, frame.height)
        }
        timeView.text = DateUtils.formatElapsedTime(position.coerceAtLeast(0) / 1000)
        place(timeBar, position.toFloat() / duration)
        container.visibility = View.VISIBLE
    }

    fun hide() {
        container.visibility = View.INVISIBLE
        clearSheet()
    }

    fun release() {
        storyboard = null
        hide()
    }

    private fun place(timeBar: View, fraction: Float) {
        val parent = container.parent as? View ?: return
        timeBar.getLocationInWindow(location)
        val timeBarX = location[0]
        parent.getLocationInWindow(location)
        val inset = timeBar.paddingLeft + SCRUBBER_INSET_DP * density
        val barWidth = timeBar.width - timeBar.paddingLeft - timeBar.paddingRight - 2 * SCRUBBER_INSET_DP * density
        val thumbX = timeBarX - location[0] + inset + barWidth * fraction.coerceIn(0f, 1f)
        container.translationX = previewLeft(thumbX, container.width, parent.width, EDGE_MARGIN_DP * density) - container.left
    }

    private fun load(url: String) {
        val generation = ++requestGeneration
        request?.dispose()
        sheetUrl = url
        sheet = null
        frameView.clearFrame()
        val context = container.context
        request = imageLoader.enqueue(
            ImageRequest.Builder(context).apply {
                data(url)
                size(Size.ORIGINAL)
                bitmapConfig(Bitmap.Config.RGB_565)
                allowHardware(false)
                target(
                    onSuccess = { image ->
                        val bitmap = (image as? BitmapImage)?.bitmap
                        if (generation == requestGeneration && url == sheetUrl && bitmap != null) {
                            sheet = bitmap
                            frame?.let { frameView.setFrame(bitmap, it.left, it.top, it.width, it.height) }
                        }
                    }
                )
            }.build()
        )
    }

    private fun clearSheet() {
        requestGeneration++
        request?.dispose()
        request = null
        sheetUrl = null
        sheet = null
        frame = null
        frameView.clearFrame()
    }

    companion object {
        /** Half of `DefaultTimeBar`'s default dragged scrubber size, which insets the bar at both ends. */
        private const val SCRUBBER_INSET_DP = 8
        private const val EDGE_MARGIN_DP = 8

        /** Left edge for a preview centred on [thumbX], kept [margin] inside the parent where it fits. */
        fun previewLeft(thumbX: Float, previewWidth: Int, parentWidth: Int, margin: Float): Float {
            return (thumbX - previewWidth / 2f).coerceIn(margin, (parentWidth - previewWidth - margin).coerceAtLeast(margin))
        }
    }
}
