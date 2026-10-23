package com.github.andreyasadchy.xtra.player.lowlatency

import android.app.Application
import android.net.Uri
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [23, 28])
class HlsMediaCompatibilityTest {
    @Test fun `Twitch live prefetch retains timing and live window`() {
        val playlist = parse("""
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-PROGRAM-DATE-TIME:2026-10-20T12:00:00Z
            #EXTINF:2.0,
            live100.ts
            #EXT-X-TWITCH-PREFETCH:https://example.com/live101.ts
        """)
        assertEquals(100L, playlist.mediaSequence)
        assertEquals(listOf("live100.ts", "https://example.com/live101.ts"), playlist.segments.map { it.url })
        assertEquals(listOf(0L, 2_000_000L), playlist.segments.map { it.relativeStartTimeUs })
        assertEquals(4_000_000L, playlist.durationUs)
        assertTrue(playlist.hasProgramDateTime)
        assertFalse(playlist.hasEndTag)
    }

    @Test fun `VoD preserves end and offset while using muted segment fallback`() {
        val playlist = parse("""
            #EXTM3U
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-TARGETDURATION:10
            #EXT-X-START:TIME-OFFSET=3.5,PRECISE=YES
            #EXTINF:10.0,
            0-unmuted.ts
            #EXTINF:4.5,
            1.ts
            #EXT-X-ENDLIST
        """)
        assertTrue(playlist.hasEndTag)
        assertEquals(HlsMediaPlaylist.PLAYLIST_TYPE_VOD, playlist.playlistType)
        assertEquals(3_500_000L, playlist.startOffsetUs)
        assertEquals(14_500_000L, playlist.durationUs)
        assertEquals("0-muted.ts", playlist.segments.first().url)
        assertEquals(10_000_000L, playlist.segments.last().relativeStartTimeUs)
    }

    @Test fun `init map survives playlist refresh and can be replaced`() {
        val first = parse("""
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="100@20"
            #EXTINF:2.0,
            first.m4s
        """)
        val refresh = parse("""
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:1
            #EXTINF:2.0,
            second.m4s
        """, first)
        val retained = requireNotNull(refresh.segments.single().initializationSegment)
        assertSame(first.lastSeenInitSegment, retained)
        assertEquals("init.mp4", retained.url)
        assertEquals(20L, retained.byteRangeOffset)
        assertEquals(100L, retained.byteRangeLength)
        val replaced = parse("""
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:2
            #EXT-X-MAP:URI="next-init.mp4"
            #EXTINF:2.0,
            third.m4s
        """, refresh)
        assertEquals("next-init.mp4", replaced.lastSeenInitSegment?.url)
        assertSame(replaced.lastSeenInitSegment, replaced.segments.single().initializationSegment)
    }

    @Test fun `part-only playlist keeps init map across zero-skip delta update`() {
        val first = parse("""
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-PART-INF:PART-TARGET=0.5
            #EXT-X-SERVER-CONTROL:CAN-BLOCK-RELOAD=YES,PART-HOLD-BACK=1.5
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-PART:DURATION=0.5,URI="part0.m4s",INDEPENDENT=YES
            #EXT-X-PRELOAD-HINT:TYPE=PART,URI="part1.m4s"
        """)
        val refresh = parse("""
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-PART-INF:PART-TARGET=0.5
            #EXT-X-SERVER-CONTROL:CAN-BLOCK-RELOAD=YES,PART-HOLD-BACK=1.5
            #EXT-X-SKIP:SKIPPED-SEGMENTS=0
            #EXT-X-PART:DURATION=0.5,URI="part0.m4s",INDEPENDENT=YES
            #EXT-X-PART:DURATION=0.5,URI="part1.m4s"
            #EXT-X-PRELOAD-HINT:TYPE=PART,URI="part2.m4s"
        """, first)
        assertTrue(refresh.segments.isEmpty())
        assertEquals(500_000L, refresh.partTargetDurationUs)
        assertTrue(refresh.serverControl.canBlockReload)
        assertEquals(1_500_000L, refresh.serverControl.partHoldBackUs)
        assertEquals(1_000_000L, refresh.durationUs)
        assertEquals(3, refresh.trailingParts.size)
        assertTrue(refresh.trailingParts.last().isPreload)
        refresh.trailingParts.forEach { assertSame(first.lastSeenInitSegment, it.initializationSegment) }
    }

    private fun parse(text: String, previous: HlsMediaPlaylist? = null): HlsMediaPlaylist =
        HlsPlaylistParser(HlsMultivariantPlaylist.EMPTY, previous).parse(
            Uri.parse("https://example.com/index.m3u8"), text.trimIndent().byteInputStream(),
        ) as HlsMediaPlaylist
}
