package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.github.andreyasadchy.xtra.model.VideoQuality
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class PlayerQualityTrackSelectionTest {
    private fun format(height: Int, fps: Float, codec: String = "avc1.64002a") =
        Format.Builder().setSampleMimeType(if (codec.startsWith("av01")) "video/av01" else "video/avc")
            .setHeight(height).setFrameRate(fps).setPeakBitrate(3000000).setCodecs(codec).build()

    @Test fun `selection traverses supported tracks in different codec groups`() {
        val avc = TrackGroup("avc", format(1080, 60f))
        val av1 = TrackGroup("av1", format(1080, 60f, codec = "av01.0.08M.08"))
        val groups = listOf(
            Tracks.Group(avc, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
            Tracks.Group(av1, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)),
        )
        val override = selectQualityTrack(VideoQuality("1080p60", "av01.0.08M.08"), groups)
        assertEquals(av1, override?.mediaTrackGroup)
        assertEquals(listOf(0), override?.trackIndices)
        val unsupported = listOf(Tracks.Group(av1, false, intArrayOf(C.FORMAT_UNSUPPORTED_TYPE), booleanArrayOf(false)))
        assertNull(selectQualityTrack(VideoQuality("1080p60", "av01.0.08M.08"), unsupported))
    }
}
