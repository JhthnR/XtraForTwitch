package com.github.andreyasadchy.xtra.ui.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.forEach
import androidx.transition.TransitionValues
import androidx.transition.Visibility

/**
 * Shared X-axis motion between settings screens, search included, so both sides of
 * every navigation move on the same axis. The transition is seekable, so the
 * predictive back gesture scrubs it instead of leaving both screens on top of each
 * other. Call [settingsMotionGroup] on the screen's root view so it moves as one.
 */
internal fun Fragment.applySettingsMotion() {
    enterTransition = SettingsAxisTransition(forward = true)
    exitTransition = SettingsAxisTransition(forward = true)
    returnTransition = SettingsAxisTransition(forward = false)
    reenterTransition = SettingsAxisTransition(forward = false)
}

internal fun View.settingsMotionGroup() {
    (this as? ViewGroup)?.isTransitionGroup = true
}

/**
 * Material shared X axis: a short slide on the standard easing with a fade-through,
 * the outgoing screen gone by [FADE_THROUGH] before the incoming one fades in.
 * Plain keyframed animators, so seeking and reversing (a cancelled back gesture)
 * land on exact values.
 */
internal class SettingsAxisTransition(private val forward: Boolean) : Visibility() {

    init {
        duration = DURATION
    }

    override fun isSeekingSupported() = true

    override fun onAppear(sceneRoot: ViewGroup, view: View, startValues: TransitionValues?, endValues: TransitionValues?): Animator =
        animator(view, appearing = true)

    override fun onDisappear(sceneRoot: ViewGroup, view: View, startValues: TransitionValues?, endValues: TransitionValues?): Animator =
        animator(view, appearing = false)

    private fun animator(view: View, appearing: Boolean): Animator {
        val rtl = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
        val distance = SLIDE_DP * view.resources.displayMetrics.density * rtl * if (forward) 1f else -1f
        val translation = PropertyValuesHolder.ofKeyframe(
            View.TRANSLATION_X,
            Keyframe.ofFloat(0f, if (appearing) distance else 0f),
            Keyframe.ofFloat(1f, if (appearing) 0f else -distance).apply { interpolator = standardEasing },
        )
        val alpha = PropertyValuesHolder.ofKeyframe(
            View.ALPHA,
            Keyframe.ofFloat(0f, if (appearing) 0f else 1f),
            Keyframe.ofFloat(FADE_THROUGH, 0f),
            Keyframe.ofFloat(1f, if (appearing) 1f else 0f),
        )
        return ObjectAnimator.ofPropertyValuesHolder(view, translation, alpha).apply {
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.translationX = 0f
                    view.alpha = 1f
                }
            })
        }
    }

    private companion object {
        const val DURATION = 350L
        const val SLIDE_DP = 30f
        const val FADE_THROUGH = 0.35f
        val standardEasing = PathInterpolatorCompat.create(0.2f, 0f, 0f, 1f)
    }
}

/** Every non-group preference, including those inside categories, in screen order. */
internal fun PreferenceGroup.leafPreferences(): List<Preference> = buildList {
    this@leafPreferences.forEach { if (it is PreferenceGroup) addAll(it.leafPreferences()) else add(it) }
}
