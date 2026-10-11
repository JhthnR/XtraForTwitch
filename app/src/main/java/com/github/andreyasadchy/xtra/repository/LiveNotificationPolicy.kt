package com.github.andreyasadchy.xtra.repository

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Stable broadcast identity on API 23 too, including Twitch's fractional ISO timestamps. */
internal fun liveNotificationStartedAt(createdAt: String?): Long? {
    val match = createdAt?.let { NOTIFICATION_TIMESTAMP.matchEntire(it) } ?: return null
    val fraction = match.groupValues[2].padEnd(3, '0').take(3)
    val zone = match.groupValues[3].let { if (it == "Z") "+0000" else it.replace(":", "") }
    val normalized = "${match.groupValues[1]}.$fraction$zone"
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    return format.parse(normalized, position)?.time?.takeIf { it > 0 && position.index == normalized.length }
}

private val NOTIFICATION_TIMESTAMP = Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})")
