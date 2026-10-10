package com.github.andreyasadchy.xtra.ui.player

import androidx.media3.common.Format
import com.github.andreyasadchy.xtra.model.VideoQuality
import org.junit.Assert.*
import org.junit.Test

class PlayerQualitySelectionTest {
    private fun format(height: Int, fps: Float, bitrate: Int = 3000000, codec: String = "avc1.64002a", id: String? = null) =
        Format.Builder().setSampleMimeType(if (codec.startsWith("av01")) "video/av01" else "video/avc").setHeight(height).setFrameRate(fps).setPeakBitrate(bitrate).setCodecs(codec).setId(id).build()

    @Test fun `fallback prioritizes resolution over frame rate regardless of input order`() {
        val formats = listOf(format(720, 60f), format(1080, 30f), format(480, 60f))
        assertEquals(1, selectQualityFormatIndex(VideoQuality("1440p60"), formats))
        assertEquals(1, selectQualityFormatIndex(VideoQuality("1080p"), formats))
        assertEquals(0, selectQualityFormatIndex(VideoQuality("720p60"), formats))
    }

    @Test fun `exact bitrate and codec selects corresponding duplicate`() {
        val formats = listOf(format(1080, 60f, 6000000), format(1080, 60f, 3000000), format(1080, 60f, 3000000, "av01.0.08M.08"))
        assertEquals(1, selectQualityFormatIndex(VideoQuality("1080p60", "avc1.64002a,mp4a.40.2", bitrate = 3000000), formats))
        assertEquals(2, selectQualityFormatIndex(VideoQuality("1080p60", "av01.0.08M.08", bitrate = 3000000), formats))
    }

    @Test fun `legacy name-only choice picks the highest bitrate duplicate regardless of order`() {
        val formats = listOf(format(1080, 60f, 3000000), format(1080, 60f, 6000000), format(720, 60f, 9000000))
        assertEquals(1, selectQualityFormatIndex(VideoQuality("1080p60"), formats))
    }

    @Test fun `format identity disambiguates otherwise identical variants`() {
        val formats = listOf(format(1080, 60f, id = "first"), format(1080, 60f, id = "second"))
        assertEquals(1, selectQualityFormatIndex(VideoQuality("1080p60", formatId = "second"), formats))
    }

    @Test fun `metadata resolution wins over misleading label`() {
        assertEquals(1, selectQualityFormatIndex(VideoQuality("source", resolution = 1080, frameRate = 30f), listOf(format(720, 60f), format(1080, 30f))))
    }

    @Test fun `source without metadata selects highest resolution`() {
        assertEquals(1, selectQualityFormatIndex(VideoQuality("source"), listOf(format(720, 60f), format(1080, 30f))))
    }

    @Test fun `fractional frame rates and missing frame rates remain selectable`() {
        assertEquals(0, selectQualityFormatIndex(VideoQuality("1080p60"), listOf(format(1080, 59.94f))))
        assertEquals(0, selectQualityFormatIndex(VideoQuality("720p"), listOf(format(720, -1f))))
    }

    @Test fun `fallback uses lowest available when all variants exceed request`() {
        assertEquals(1, selectQualityFormatIndex(VideoQuality("160p"), listOf(format(1080, 60f), format(360, 30f))))
        assertNull(selectQualityFormatIndex(VideoQuality("720p"), emptyList()))
    }

    @Test fun `default quality ordering and saved source are independent of catalog order`() {
        val low = VideoQuality("720p60")
        val high = VideoQuality("1080p")
        val list = listOf(VideoQuality("auto"), low, VideoQuality("audio_only"), high)
        assertSame(high, selectPreferredQuality(list, "1440p60"))
        assertSame(high, selectPreferredQuality(list, "source"))
        assertSame(high, selectPreferredQuality(list, "Source"))
        assertSame(low, selectPreferredQuality(list, "720p60"))
        assertEquals("audio_only", selectPreferredQuality(list, "audio_only")?.name)
        assertNull(selectPreferredQuality(emptyList(), "720p"))
    }

    @Test fun `download selection retains identity across refreshed signed urls`() {
        val first = VideoQuality("1080p60", "avc1", "https://example.com/one.m3u8?token=new", 6000000)
        val second = VideoQuality("1080p60", "avc1", "https://example.com/two.m3u8?token=new", 6000000)
        val requested = second.copy(url = "https://example.com/two.m3u8?token=old")
        assertSame(second, selectDownloadQuality(listOf(first, second), requested))
        assertSame(second, selectDownloadQuality(listOf(first, second), requested.copy(url = "/two.m3u8")))
    }

    @Test fun `download selection uses bitrate when URL changes and accepts legacy audio`() {
        val low = VideoQuality("1080p60", "avc1", "https://example.com/low.m3u8", 3000000)
        val high = low.copy(url = "https://example.com/high.m3u8", bitrate = 6000000)
        assertSame(high, selectDownloadQuality(listOf(low, high), high.copy(url = null)))
        val audio = VideoQuality("audio_only")
        assertSame(audio, selectDownloadQuality(listOf(low, audio), VideoQuality("audio")))
    }

    @Test fun `format metadata survives conversion without unknown sentinel values`() {
        val quality = format(1080, 59.94f, 6000000, id = "variant-2").toVideoQuality("1080p60", "https://example.com/2.m3u8")
        assertEquals(6000000, quality.bitrate)
        assertEquals(1080, quality.resolution)
        assertEquals(59.94f, quality.frameRate)
        assertEquals("variant-2", quality.formatId)
        val unknown = Format.Builder().build().toVideoQuality("source", "https://example.com/1.m3u8")
        assertNull(unknown.resolution)
        assertNull(unknown.frameRate)
        assertNull(unknown.bitrate)
    }
    @Test fun `bitrate fallback does not upgrade to a higher bandwidth duplicate`() {
        val formats = listOf(format(1080, 60f, 6000000), format(1080, 60f, 3000000))
        assertEquals(1, selectQualityFormatIndex(VideoQuality("1080p60", bitrate = 4000000), formats))
    }
    @Test fun `source exact metadata does not invent a 30 FPS limit when HLS omits frame rate`() {
        val formats = listOf(format(720, 30f), format(1080, 60f, 6000000, id = "source"))
        val requested = VideoQuality("source", "avc1.64002a", bitrate = 6000000, resolution = 1080, formatId = "source")
        assertEquals(1, selectQualityFormatIndex(requested, formats))
    }
    @Test fun `refreshed download path wins when bitrate is omitted or revised`() {
        val high = VideoQuality("1080p60", "avc1", "https://example.com/high.m3u8", 6000000)
        val low = VideoQuality("1080p60", "av01.0.08M.08", "https://example.com/low.m3u8", null)
        val requested = low.copy(bitrate = 3000000)
        assertSame(low, selectDownloadQuality(listOf(high, low), requested))
        assertSame(high, selectDownloadQuality(listOf(high, low), high.copy(bitrate = 4000000)))
        val middle = high.copy(url = "https://example.com/middle.m3u8", bitrate = 3000000)
        assertSame(middle, selectDownloadQuality(listOf(high, middle), high.copy(url = null, bitrate = 4000000)))
    }

    @Test fun `catalog refresh preserves manual variants and remaps expired urls`() {
        val old = VideoQuality("480p", "avc1", "https://example.com/480?token=old", 1000000)
        val fresh = old.copy(url = "https://example.com/480?token=new")
        val high = VideoQuality("1080p60", "avc1", "https://example.com/high", 6000000)
        assertSame(fresh, preserveRefreshedQuality(listOf(high, fresh), old, true))
        assertNull(preserveRefreshedQuality(listOf(high, fresh), old, false))
        val audio = VideoQuality("audio_only", "mp4a", "https://example.com/audio?token=new")
        assertSame(audio, preserveRefreshedQuality(listOf(high, audio), audio.copy(url = "https://example.com/audio?token=old"), false))
    }
    @Test fun `duplicate audio variants keep exact path codec and bitrate after resume`() {
        val first = VideoQuality("audio_only", "mp4a.40.2", "https://example.com/aac.m3u8", 128000)
        val second = VideoQuality("audio_only", "opus", "https://example.com/opus.m3u8", 128000)
        assertSame(second, selectDownloadQuality(listOf(first, second), second.copy(name = "audio", url = "/opus.m3u8")))
        assertSame(first, selectDownloadQuality(listOf(first, second), VideoQuality("audio")))
    }
    @Test fun `saved source bitrate fallback respects requested bandwidth without resolution metadata`() {
        val high = VideoQuality("source", "avc1", "https://example.com/high", 6000000)
        val low = high.copy(url = "https://example.com/low", bitrate = 2000000)
        assertSame(low, selectDownloadQuality(listOf(high, low), VideoQuality("source", "avc1", bitrate = 3000000)))
    }

    // Channel B's variants run at different bitrates and codec levels than where the choice was saved.
    private val channelB = listOf(
        VideoQuality("1080p60", "avc1.640028,mp4a.40.2", "https://example.com/1080.m3u8", 8000000, 1080, 60f),
        VideoQuality("720p60", "avc1.4D401F,mp4a.40.2", "https://example.com/720.m3u8", 3400000, 720, 60f),
        VideoQuality("480p", "avc1.4D401E,mp4a.40.2", "https://example.com/480.m3u8", 1400000, 480, 30f),
    )

    @Test fun `saved bitrate from another channel never drops a rung`() {
        val saved = VideoQuality("1080p60", "avc1.64002A,mp4a.40.2", bitrate = 6000000)
        assertSame(channelB[0], selectDownloadQuality(channelB, saved))
        val source = channelB.map { if (it.resolution == 1080) it.copy(name = "source") else it }
        assertEquals("source", selectDownloadQuality(source, VideoQuality("source", "avc1.64002A,mp4a.40.2", bitrate = 6000000))?.name)
        val formats = channelB.map { format(it.resolution!!, it.frameRate!!, it.bitrate!!, it.codecs!!) }
        assertEquals(0, selectQualityFormatIndex(saved, formats))
        assertEquals(0, selectQualityFormatIndex(VideoQuality("source", bitrate = 6000000), formats))
    }

    @Test fun `codec family preference never selects a higher resolution than requested`() {
        val formats = listOf(
            format(1440, 60f, 9000000, "hev1.1.6.L150.90,mp4a.40.2"),
            format(1080, 60f, 6000000, "avc1.64002A,mp4a.40.2"),
            format(720, 60f, 3400000, "avc1.4D401F,mp4a.40.2"),
        )
        assertEquals(1, selectQualityFormatIndex(VideoQuality("1080p60", "hev1.1.6.L150.90,mp4a.40.2"), formats))
    }

    @Test fun `codec family breaks ties within a rung across profile and level strings`() {
        val formats = listOf(format(1080, 60f, 6000000, "avc1.64002A"), format(1080, 60f, 6000000, "hvc1.1.6.L123.B0"))
        assertEquals(1, selectQualityFormatIndex(VideoQuality("1080p60", "hev1.1.6.L120.90", bitrate = 5000000), formats))
        assertEquals(0, selectQualityFormatIndex(VideoQuality("1080p60", "avc1.640028"), formats))
    }
}
