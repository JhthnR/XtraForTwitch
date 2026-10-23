package com.github.andreyasadchy.xtra.ui.login

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout

/** LoginActivity retains its WebViews across rotation, so adapt without reinflating it. */
class DeviceCodeContentLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private var wide: Boolean? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val nextWide = MeasureSpec.getSize(widthMeasureSpec) / resources.displayMetrics.density >= 600
        if (wide != nextWide && childCount == 2) {
            wide = nextWide
            orientation = if (nextWide) HORIZONTAL else VERTICAL
            getChildAt(0).layoutParams = (getChildAt(0).layoutParams as LayoutParams).apply {
                gravity = if (nextWide) Gravity.TOP else Gravity.CENTER_HORIZONTAL
                marginEnd = if (nextWide) (32 * resources.displayMetrics.density).toInt() else 0
            }
            getChildAt(1).layoutParams = (getChildAt(1).layoutParams as LayoutParams).apply {
                width = if (nextWide) 0 else ViewGroup.LayoutParams.MATCH_PARENT
                weight = if (nextWide) 1f else 0f
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
