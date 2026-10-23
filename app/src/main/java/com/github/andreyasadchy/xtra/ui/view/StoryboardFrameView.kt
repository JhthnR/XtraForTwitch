package com.github.andreyasadchy.xtra.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

/** Draws one frame of a storyboard sprite sheet, scaled to the view. */
class StoryboardFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = Rect()
    private var sheet: Bitmap? = null

    init {
        clipToOutline = true
    }

    fun setFrame(sheet: Bitmap?, left: Int, top: Int, width: Int, height: Int) {
        if (sheet == null || left < 0 || top < 0 || width <= 0 || height <= 0 ||
            left.toLong() + width > sheet.width || top.toLong() + height > sheet.height
        ) {
            clearFrame()
            return
        }
        this.sheet = sheet
        source.set(left, top, left + width, top + height)
        invalidate()
    }

    fun clearFrame() {
        if (sheet != null) {
            sheet = null
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val sheet = sheet ?: return
        destination.set(0, 0, width, height)
        canvas.drawBitmap(sheet, source, destination, paint)
    }
}
