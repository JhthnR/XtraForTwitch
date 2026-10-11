package com.github.andreyasadchy.xtra.util

import android.graphics.Rect
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

val View.isKeyboardShown: Boolean
    get() {
        val rect = Rect()
        getWindowVisibleDisplayFrame(rect)
        val screenHeight = rootView.height

        // rect.bottom is the position above soft keypad or device button.
        // if keypad is shown, the r.bottom is smaller than that before.
        val keypadHeight = screenHeight - rect.bottom
        return keypadHeight > screenHeight * 0.15
    }

/**
 * Adds [listener] to the window's tree observer only while this view is attached.
 * A listener added straight to `viewTreeObserver` stays on the window after the
 * view is gone and keeps whatever it captured, such as a closed player fragment.
 */
fun View.addWindowGlobalLayoutListener(listener: ViewTreeObserver.OnGlobalLayoutListener) {
    if (isAttachedToWindow) viewTreeObserver.addOnGlobalLayoutListener(listener)
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        override fun onViewDetachedFromWindow(view: View) = view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    })
}

fun ViewPager2.reduceDragSensitivity() {
    try {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * 2)
    } catch (e: Exception) {
    }
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

fun View.enable() {
    isEnabled = true
    alpha = 1f
}

fun View.disable() {
    isEnabled = false
    alpha = 0.6f
}
