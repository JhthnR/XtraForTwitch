package com.github.andreyasadchy.xtra.ui.player

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.util.C

/**
 * Narrow storage interface so the store's persistence and migration logic is
 * unit-testable without Android.
 */
interface DisplayModeStorage {
    fun stringValue(key: String): String?
    fun intValue(key: String): Int?
    fun floatValue(key: String): Float?
    fun putStringValue(key: String, value: String)
    fun putFloatValue(key: String, value: Float)
    fun removeValue(key: String)
}

class SharedPreferencesDisplayModeStorage(private val prefs: SharedPreferences) : DisplayModeStorage {
    override fun stringValue(key: String): String? = prefs.getString(key, null)
    override fun intValue(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null
    override fun floatValue(key: String): Float? = if (prefs.contains(key)) runCatching { prefs.getFloat(key, 1f) }.getOrNull() else null
    override fun putStringValue(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    override fun putFloatValue(key: String, value: Float) {
        prefs.edit { putFloat(key, value) }
    }
    override fun removeValue(key: String) {
        prefs.edit { remove(key) }
    }
}

/**
 * Owns persistence and legacy migration for the non-portrait maximized
 * player's display mode.
 *
 * The canonical preference key is [C.PLAYER_DISPLAY_MODE_LANDSCAPE] with
 * string values fit/fill/stretch. On first read when the canonical key is
 * absent, the legacy [C.ASPECT_RATIO_LANDSCAPE] integer migrates:
 * 0 to Fit; 1, 2 and 4 to Fill; 3 to Stretch; missing, corrupt or unknown to
 * Fit. The legacy key is never written; it remains read-only migration input
 * so upgrades are deterministic and downgrades are not disturbed.
 *
 * A pinch zoom between Fit and Fill is stored as mode fit plus
 * [C.PLAYER_DISPLAY_ZOOM_LANDSCAPE], relative to Fit (see [PinchZoomController]),
 * so builds without free zoom read plain Fit.
 */
class PlayerDisplayModeStore(private val storage: DisplayModeStorage) {

    fun loadDisplayMode(): PlayerDisplayMode {
        storage.stringValue(C.PLAYER_DISPLAY_MODE_LANDSCAPE)?.let { canonical ->
            return PlayerDisplayMode.fromName(canonical) ?: migrateLegacy()
        }
        return migrateLegacy()
    }

    /** Fit-relative zoom kept with Fit; 1 when absent or invalid. */
    fun loadZoom(): Float {
        val zoom = storage.floatValue(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE) ?: return 1f
        return if (zoom.isFinite() && zoom > 1f) zoom else 1f
    }

    /** Saves [mode]; [zoom] is kept only with Fit, and any other choice clears it. */
    fun saveDisplayMode(mode: PlayerDisplayMode, zoom: Float = 1f) {
        storage.putStringValue(C.PLAYER_DISPLAY_MODE_LANDSCAPE, mode.preferenceName)
        if (mode == PlayerDisplayMode.FIT && zoom.isFinite() && zoom > 1f) {
            storage.putFloatValue(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE, zoom)
        } else {
            storage.removeValue(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE)
        }
    }

    private fun migrateLegacy(): PlayerDisplayMode {
        val mode = when (storage.intValue(C.ASPECT_RATIO_LANDSCAPE)) {
            0 -> PlayerDisplayMode.FIT
            1, 2, 4 -> PlayerDisplayMode.FILL
            3 -> PlayerDisplayMode.STRETCH
            else -> PlayerDisplayMode.FIT
        }
        storage.putStringValue(C.PLAYER_DISPLAY_MODE_LANDSCAPE, mode.preferenceName)
        return mode
    }
}

val PlayerDisplayMode.preferenceName: String
    get() = when (this) {
        PlayerDisplayMode.FIT -> PlayerDisplayMode.FIT_NAME
        PlayerDisplayMode.FILL -> PlayerDisplayMode.FILL_NAME
        PlayerDisplayMode.STRETCH -> PlayerDisplayMode.STRETCH_NAME
    }
