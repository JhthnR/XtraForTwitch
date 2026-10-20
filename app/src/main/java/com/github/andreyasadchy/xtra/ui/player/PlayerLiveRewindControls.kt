package com.github.andreyasadchy.xtra.ui.player

import android.view.View
import android.widget.RelativeLayout
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.github.andreyasadchy.xtra.databinding.PlayerLayoutBinding
import kotlin.math.min

/** Keep the portrait live action row clear of uptime and viewer counts. */
internal fun PlayerLayoutBinding.bindLiveRewindButton(show: Boolean, isPortrait: Boolean, onClick: () -> Unit) {
    liveRewind.isVisible = show
    liveRewind.setOnClickListener(if (show) View.OnClickListener { onClick() } else null)
    val separateInfoRow = show && isPortrait
    val anchor = if (separateInfoRow) streamInfoLayout.id else bottomLayout.id
    for (row in listOf(bottomLeftLayout, bottomRightLayout)) {
        if ((row.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.ABOVE) != anchor) {
            row.updateLayoutParams<RelativeLayout.LayoutParams> { addRule(RelativeLayout.ABOVE, anchor) }
        }
    }
    playPause.translationY = 0f
    if (separateInfoRow) {
        root.doOnLayout {
            // A newer orientation update can supersede this deferred layout.
            if ((bottomLeftLayout.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.ABOVE) == streamInfoLayout.id) {
                val gap = 8 * root.resources.displayMetrics.density
                playPause.translationY = min(0f, bottomLeftLayout.top - gap - playPause.bottom)
            }
        }
    }
}
