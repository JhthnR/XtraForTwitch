package com.github.andreyasadchy.xtra.util

import org.junit.Assert.*
import org.junit.Test

class PlaylistQualitiesTest {
    @Test fun `variant attributes never drift when optional fields are missing`() {
        val variants = parsePlaylistQualities("""
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,AVERAGE-BANDWIDTH=5000000,IVS-NAME="1080p60",CODECS="avc1.64002a,mp4a.40.2",RESOLUTION=1920x1080,FRAME-RATE=59.94
            https://example.com/high.m3u8?token=high
            #EXT-X-STREAM-INF:IVS-NAME="720p60",RESOLUTION=1280x720
            https://example.com/middle.m3u8?token=middle
            #EXT-X-STREAM-INF:IVS-NAME="480p",BANDWIDTH=1500000,CODECS="av01.0.04M.08"
            https://example.com/low.m3u8
        """.trimIndent())
        assertEquals(3, variants.size)
        assertEquals(listOf(6000000, null, 1500000), variants.map { it.bitrate })
        assertEquals(listOf("avc1.64002a,mp4a.40.2", null, "av01.0.04M.08"), variants.map { it.codecs })
        assertEquals("https://example.com/high.m3u8?token=high", variants[0].url)
        assertEquals(1080, variants[0].resolution)
        assertEquals(59.94f, variants[0].frameRate)
    }

    @Test fun `media group names and relative query urls are retained`() {
        val variants = parsePlaylistQualities("""
            #EXTM3U
            #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="high",NAME="720p60"
            #EXT-X-STREAM-INF:VIDEO="high",BANDWIDTH=2500000,CODECS="avc1.64002a,mp4a.40.2"
            ../high/index.m3u8?token=123
        """.trimIndent(), "https://example.com/master/index.m3u8")
        assertEquals("720p60", variants.single().name)
        assertEquals("https://example.com/high/index.m3u8?token=123", variants.single().url)
    }

    @Test fun `resolution fallback audio and chunked retain readable names`() {
        val variants = parsePlaylistQualities("""
            #EXT-X-STREAM-INF:RESOLUTION=1920x1080,FRAME-RATE=59.94
            https://example.com/video.m3u8
            #EXT-X-STREAM-INF:CODECS="mp4a.40.2",BANDWIDTH=100000
            https://example.com/audio.m3u8
            #EXT-X-STREAM-INF:IVS-NAME="chunked"
            https://example.com/source.m3u8
        """.trimIndent())
        assertEquals(listOf("1080p60", "audio_only", "source"), variants.map { it.name })
    }

    @Test fun `invalid values and unsupported or unresolved URLs are skipped safely`() {
        val variants = parsePlaylistQualities("""
            #EXT-X-STREAM-INF:IVS-NAME="720p",BANDWIDTH=-1,FRAME-RATE=NaN,RESOLUTION=bad
            https://example.com/valid.m3u8
            #EXT-X-STREAM-INF:IVS-NAME="1080p"
            file:///private/video.m3u8
            #EXT-X-STREAM-INF:IVS-NAME="360p"
            relative.m3u8
        """.trimIndent())
        assertEquals(1, variants.size)
        assertNull(variants.single().bitrate)
        assertNull(variants.single().frameRate)
        assertNull(variants.single().resolution)
        assertTrue(parsePlaylistQualities("#EXTM3U\n#EXTINF:3,\nhttps://example.com/segment.ts").isEmpty())
    }
}
