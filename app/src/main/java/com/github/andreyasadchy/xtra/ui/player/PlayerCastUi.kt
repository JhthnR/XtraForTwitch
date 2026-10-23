package com.github.andreyasadchy.xtra.ui.player

import android.view.View
import android.view.ViewStub
import androidx.fragment.app.DialogFragment
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import androidx.mediarouter.app.MediaRouteDialogFactory
import com.google.android.gms.cast.framework.CastButtonFactory

/** The player owns any route dialog it opens, including during close/replacement. */
internal class PlayerCastUi(stub: ViewStub) {
    private val button = stub.inflate() as MediaRouteButton
    private var dialog: DialogFragment? = null
    private var available = true

    init {
        try {
            CastButtonFactory.setUpMediaRouteButton(button.context, button)
        } catch (_: RuntimeException) {
            available = false
            button.visibility = View.GONE
        }
        val factory = button.dialogFactory
        button.dialogFactory = object : MediaRouteDialogFactory() {
            override fun onCreateChooserDialogFragment(): MediaRouteChooserDialogFragment =
                factory.onCreateChooserDialogFragment().also { dialog = it }
            override fun onCreateControllerDialogFragment(): MediaRouteControllerDialogFragment =
                factory.onCreateControllerDialogFragment().also { dialog = it }
        }
    }

    fun dismissDialog() { dialog?.dismissAllowingStateLoss(); dialog = null }
    fun setVisible(visible: Boolean) {
        if (!visible) dismissDialog()
        button.visibility = if (visible && available) View.VISIBLE else View.GONE
    }
    fun dispose() { dismissDialog(); button.visibility = View.GONE }
}
