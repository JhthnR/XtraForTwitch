package com.github.andreyasadchy.xtra.ui.player

import android.graphics.Rect
import android.util.Rational

internal object PlayerPipPolicy {
    data class ChatEligibility(
        val enabled: Boolean,
        val live: Boolean,
        val portrait: Boolean,
        val maximized: Boolean,
        val chatShown: Boolean,
        val chatReady: Boolean,
        val local: Boolean,
        val television: Boolean,
        val floating: Boolean,
        val splitAtHinge: Boolean,
    ) {
        fun canShow() = enabled && live && portrait && maximized && chatShown &&
            chatReady && local && !television && !floating && !splitAtHinge
    }

    data class Presentation(val aspectRatio: Rational, val source: Rect, val chat: Boolean)

    fun presentation(video: Rect, content: Rect, chat: Boolean): Presentation? {
        val source = Rect(if (chat) content else video)
        if (source.width() <= 0 || source.height() <= 0) return null
        val ratio = if (chat) {
            source.bottom = minOf(source.bottom, source.top + (source.width().toLong() * 16 / 9).toInt())
            Rational(9, 16)
        } else {
            // Reset a prior chat request as well as preserving legacy video-only PiP.
            Rational(16, 9)
        }
        return Presentation(ratio, source, chat)
    }
}
