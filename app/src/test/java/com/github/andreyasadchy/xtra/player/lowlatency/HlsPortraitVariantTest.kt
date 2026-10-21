package com.github.andreyasadchy.xtra.player.lowlatency

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.media3.common.Format
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [23, 28])
class HlsPortraitVariantTest {
    @Test fun `portrait variants of dual-format streams are not offered`() {
        val unavailable = Base64.encodeToString("""
            [{"IVS_NAME":"1080p60","STABLE-VARIANT-ID":"1080p60-portrait","FILTER_REASONS":["FR_DUALFORMAT_INCOMPATIBLE"],"BANDWIDTH":6000000,"RESOLUTION":"1080x1920"},
             {"IVS_NAME":"720p60","STABLE-VARIANT-ID":"720p60-portrait","FILTER_REASONS":[],"BANDWIDTH":3000000,"RESOLUTION":"720x1280"},
             {"IVS_NAME":"160p","STABLE-VARIANT-ID":"160p30","FILTER_REASONS":[],"BANDWIDTH":230000,"RESOLUTION":"284x160"}]
        """.trimIndent().toByteArray(), Base64.NO_WRAP)
        val playlist = """
            #EXTM3U
            #EXT-X-SESSION-DATA:DATA-ID="com.amazon.ivs.unavailable-media",VALUE="$unavailable"
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2",FRAME-RATE=60.000,IVS-NAME="1080p60",STABLE-VARIANT-ID="1080p60"
            https://example.com/1080p60/index-dvr.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=720x1280,CODECS="avc1.4D401F,mp4a.40.2",FRAME-RATE=30.000,IVS-NAME="720p30",STABLE-VARIANT-ID="720p30-portrait"
            https://example.com/720p30-portrait/index-dvr.m3u8
        """.trimIndent()
        val parsed = HlsPlaylistParser().parse(Uri.parse("https://example.com/master.m3u8"), playlist.byteInputStream()) as HlsMultivariantPlaylist
        assertEquals(setOf("1080p60", "160p"), parsed.variants.map { it.format.label }.toSet())
        assertEquals(2, parsed.variants.size)
        assertEquals(setOf("1080p60", "160p30"), parsed.variants.map { it.stableVariantId }.toSet())
        assertEquals(Format.NO_VALUE.toFloat(), parsed.variants.single { it.format.label == "160p" }.format.frameRate)
    }

    @Test fun `stable rendition identity keeps quality labels and variant links`() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="720p30",NAME="720p",STABLE-RENDITION-ID="video-720"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",STABLE-RENDITION-ID="audio-en",URI="audio.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2",VIDEO="720p30",AUDIO="audio",IVS-NAME="720p",STABLE-VARIANT-ID="720p30"
            video.m3u8
        """.trimIndent()
        val parsed = HlsPlaylistParser().parse(Uri.parse("https://example.com/master.m3u8"), playlist.byteInputStream()) as HlsMultivariantPlaylist
        assertEquals("720p30", parsed.variants.single().stableVariantId)
        assertEquals("audio", parsed.variants.single().audioGroupId)
        assertEquals("720p", parsed.videos.single().format.label)
        assertEquals("video-720", parsed.videos.single().stableRenditionId)
        assertEquals("audio-en", parsed.audios.single().stableRenditionId)
        assertEquals(Uri.parse("https://example.com/audio.m3u8"), parsed.audios.single().url)
    }
}
