package com.github.andreyasadchy.xtra.ui.chat

/**
 * Rebinds one chat row when its emotes, badges and name paints finish loading.
 *
 * Images already in memory load while the row binds, so they only need the row's single
 * bind. Images that finish later rebind the row at most once per frame, and only while the
 * row still shows the bind they were loaded for, so a recycled row never shows an older
 * message.
 */
class ChatRowImageBinder(
    private val postOnFrame: (Runnable) -> Unit,
    private val removeFromFrame: (Runnable) -> Unit,
) {
    private var generation = 0
    private var pendingBind: (() -> Unit)? = null
    private val rebind = Runnable {
        pendingBind?.let {
            pendingBind = null
            it()
        }
    }

    /**
     * Starts loading a row's images with [loadImages] and binds the row once with [bind].
     * [loadImages] receives the callback to call after each image is applied to the text.
     */
    fun bind(bind: () -> Unit, loadImages: (onImageLoaded: () -> Unit) -> Unit) {
        val current = ++generation
        if (pendingBind != null) {
            pendingBind = null
            removeFromFrame(rebind)
        }
        var loadingSynchronously = true
        loadImages {
            if (current == generation && !loadingSynchronously && pendingBind == null) {
                pendingBind = bind
                postOnFrame(rebind)
            }
        }
        loadingSynchronously = false
        bind()
    }

    /** Ignores images that are still loading for the current bind, for example when the row is recycled. */
    fun cancel() {
        generation++
        if (pendingBind != null) {
            pendingBind = null
            removeFromFrame(rebind)
        }
    }
}
