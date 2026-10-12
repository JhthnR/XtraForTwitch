package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import com.github.andreyasadchy.xtra.R

/**
 * One scrollable body yields space to the header and actions in short windows and large text.
 * The icon drops out when the window is short, the secondary actions stack when either label
 * would wrap, and the dividers only show while the body actually scrolls.
 */
class UpdateSheetLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    var maxAvailableHeight: Int = Int.MAX_VALUE
        set(value) {
            val height = value.coerceAtLeast(1)
            if (field != height) { field = height; requestLayout() }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) {
            resources.displayMetrics.heightPixels
        } else View.MeasureSpec.getSize(heightMeasureSpec)
        val limit = available.coerceAtMost(maxAvailableHeight)
        findViewById<View>(R.id.updateIcon)?.let { icon ->
            val visibility = if (limit / resources.displayMetrics.density < MIN_ICON_HEIGHT_DP) GONE else VISIBLE
            if (icon.visibility != visibility) icon.visibility = visibility
        }
        findViewById<LinearLayout>(R.id.secondaryActions)?.let { stackIfWrapping(it, View.MeasureSpec.getSize(widthMeasureSpec)) }
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(measuredHeight.coerceAtMost(limit), View.MeasureSpec.EXACTLY))
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        findViewById<NestedScrollView>(R.id.releaseNotesScroll)?.setOnScrollChangeListener { view: NestedScrollView, _, _, _, _ ->
            updateDividers(view)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        findViewById<NestedScrollView>(R.id.releaseNotesScroll)?.let { updateDividers(it) }
    }

    private fun updateDividers(scroll: NestedScrollView) {
        findViewById<View>(R.id.notesDividerTop)?.visibility = if (scroll.canScrollVertically(-1)) VISIBLE else INVISIBLE
        findViewById<View>(R.id.notesDividerBottom)?.visibility = if (scroll.canScrollVertically(1)) VISIBLE else INVISIBLE
    }

    /** Side by side halves only when every label fits its half on one line. */
    private fun stackIfWrapping(actions: LinearLayout, width: Int) {
        val params = actions.layoutParams as MarginLayoutParams
        val half = (width - paddingLeft - paddingRight - params.leftMargin - params.rightMargin) / 2
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val stacked = (0 until actions.childCount).map { actions.getChildAt(it) }.filter { it.visibility != GONE }.any {
            it.measure(unspecified, unspecified)
            it.measuredWidth > half
        }
        actions.orientation = if (stacked) VERTICAL else HORIZONTAL
        for (index in 0 until actions.childCount) {
            val child = actions.getChildAt(index)
            val childParams = child.layoutParams as LayoutParams
            val childWidth = if (stacked) ViewGroup.LayoutParams.MATCH_PARENT else 0
            val weight = if (stacked) 0f else 1f
            if (childParams.width != childWidth || childParams.weight != weight) {
                childParams.width = childWidth
                childParams.weight = weight
                child.layoutParams = childParams
            }
        }
    }

    private companion object {
        const val MIN_ICON_HEIGHT_DP = 420
    }
}
