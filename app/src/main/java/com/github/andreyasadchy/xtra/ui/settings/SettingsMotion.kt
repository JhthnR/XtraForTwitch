package com.github.andreyasadchy.xtra.ui.settings

import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.forEach
import com.google.android.material.transition.MaterialSharedAxis

/**
 * Material shared X-axis motion between settings screens, search included, so both
 * sides of every navigation move on the same axis. Durations follow the system
 * animator scale, so "Remove animations" still snaps.
 */
internal fun Fragment.applySettingsMotion() {
    enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
    returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
    reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
}

/** Every non-group preference, including those inside categories, in screen order. */
internal fun PreferenceGroup.leafPreferences(): List<Preference> = buildList {
    this@leafPreferences.forEach { if (it is PreferenceGroup) addAll(it.leafPreferences()) else add(it) }
}
