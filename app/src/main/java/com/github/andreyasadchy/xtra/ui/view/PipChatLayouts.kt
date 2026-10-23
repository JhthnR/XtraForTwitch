package com.github.andreyasadchy.xtra.ui.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout

/** Layout/draw suppression keeps asynchronous chat UI updates and input drafts intact. */
private class PipChatContentMode(private val owner: ViewGroup) {
    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            owner.requestLayout()
            owner.invalidate()
        }

    fun measure(width: Int, height: Int) {
        for (index in 0 until owner.childCount) {
            owner.getChildAt(index).measure(
                View.MeasureSpec.makeMeasureSpec(if (index == 0) (width - owner.paddingLeft - owner.paddingRight).coerceAtLeast(0) else 0, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(if (index == 0) (height - owner.paddingTop - owner.paddingBottom).coerceAtLeast(0) else 0, View.MeasureSpec.EXACTLY),
            )
        }
    }

    fun layout() {
        for (index in 0 until owner.childCount) {
            val child = owner.getChildAt(index)
            if (index == 0) child.layout(owner.paddingLeft, owner.paddingTop, owner.width - owner.paddingRight, owner.height - owner.paddingBottom)
            else child.layout(0, 0, 0, 0)
        }
    }

    fun allows(child: View) = !enabled || child === owner.getChildAt(0)
}

class PipChatRoot @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ConstraintLayout(context, attrs) {
    private val mode = PipChatContentMode(this)
    var firstChildOnly: Boolean
        get() = mode.enabled
        set(value) { mode.enabled = value }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!firstChildOnly) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        mode.measure(measuredWidth, measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (firstChildOnly) mode.layout() else super.onLayout(changed, left, top, right, bottom)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long) = mode.allows(child) && super.drawChild(canvas, child, drawingTime)
    // PiP has system controls; consume input before dispatch can reach chat actions.
    override fun dispatchTouchEvent(event: MotionEvent) = firstChildOnly || super.dispatchTouchEvent(event)
    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        if (firstChildOnly) getChildAt(0)?.let { outChildren.add(it) } else super.addChildrenForAccessibility(outChildren)
    }
}

class PipChatColumn @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val mode = PipChatContentMode(this)
    var firstChildOnly: Boolean
        get() = mode.enabled
        set(value) { mode.enabled = value }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!firstChildOnly) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        mode.measure(measuredWidth, measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (firstChildOnly) mode.layout() else super.onLayout(changed, left, top, right, bottom)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long) = mode.allows(child) && super.drawChild(canvas, child, drawingTime)
    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        if (firstChildOnly) getChildAt(0)?.let { outChildren.add(it) } else super.addChildrenForAccessibility(outChildren)
    }
}

class PipChatViewport @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val mode = PipChatContentMode(this)
    var firstChildOnly: Boolean
        get() = mode.enabled
        set(value) { mode.enabled = value }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!firstChildOnly) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        mode.measure(measuredWidth, measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (firstChildOnly) mode.layout() else super.onLayout(changed, left, top, right, bottom)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long) = mode.allows(child) && super.drawChild(canvas, child, drawingTime)
    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        if (firstChildOnly) getChildAt(0)?.let { outChildren.add(it) } else super.addChildrenForAccessibility(outChildren)
    }
}
