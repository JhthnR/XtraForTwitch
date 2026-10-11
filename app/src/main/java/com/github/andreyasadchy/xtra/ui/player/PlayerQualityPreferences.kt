package com.github.andreyasadchy.xtra.ui.player

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.util.C

/** Store reusable variant attributes, never a stream's signed or session-specific URL. */
internal object PlayerQualityPreferences {
    fun save(preferences: SharedPreferences, quality: VideoQuality) {
        preferences.edit {
            putString(C.PLAYER_QUALITY, quality.name)
            putString(C.PLAYER_QUALITY_CODECS, quality.codecs)
            if (quality.bitrate != null && quality.bitrate > 0) {
                putInt(C.PLAYER_QUALITY_BITRATE, quality.bitrate)
            } else {
                remove(C.PLAYER_QUALITY_BITRATE)
            }
        }
    }

    fun resolve(preferences: SharedPreferences, qualities: List<VideoQuality>): VideoQuality? = selectDownloadQuality(
        qualities,
        VideoQuality(
            name = preferences.getString(C.PLAYER_QUALITY, "720p60")?.substringBefore(' '),
            codecs = preferences.getString(C.PLAYER_QUALITY_CODECS, null),
            bitrate = preferences.getInt(C.PLAYER_QUALITY_BITRATE, -1).takeIf { it > 0 },
        ),
    )
}
