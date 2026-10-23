package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.VideoStoryboard
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VideoStoryboardTest {
    private val url = "https://example.com/vod/storyboards/123-info.json"
    private fun info(quality: String = "high", interval: Double = 10.0, cols: Int = 2) =
        VideoStoryboard.Info(7, 160, 90, cols, 2, interval, quality, listOf("a.jpg", "b.jpg"))

    @Test fun `derive index URL only from storyboard strips`() {
        assertEquals(url, VideoStoryboard.infoUrl("https://example.com/vod/storyboards/123-strip-0.jpg?x=1"))
        assertNull(VideoStoryboard.infoUrl(null))
        assertNull(VideoStoryboard.infoUrl("https://example.com/123-strip-0.jpg"))
        assertNull(VideoStoryboard.infoUrl("https://example.com/storyboards/not-a-strip.jpg"))
        assertNull(VideoStoryboard.infoUrl("file:///storyboards/123-strip-0.jpg"))
    }

    @Test fun `positions map through columns rows and sheet boundaries`() {
        val board = requireNotNull(VideoStoryboard.from(url, listOf(info())))
        assertEquals(VideoStoryboard.Frame("https://example.com/vod/storyboards/a.jpg", 0, 0, 160, 90), board.frameAt(-1))
        assertEquals(160, board.frameAt(10_000)?.left)
        assertEquals(90, board.frameAt(20_000)?.top)
        assertEquals(VideoStoryboard.Frame("https://example.com/vod/storyboards/b.jpg", 0, 0, 160, 90), board.frameAt(40_000))
        assertEquals(board.frameAt(60_000), board.frameAt(Long.MAX_VALUE))
    }

    @Test fun `parse unknown fields and prefer high then widest valid set`() {
        val json = Json { ignoreUnknownKeys = true }
        val sets = json.decodeFromString<List<VideoStoryboard.Info>>("""[
            {"count":8,"width":160,"height":90,"cols":2,"rows":2,"interval":1.5,"quality":"high","images":["a.jpg"],"unknown":true},
            {"count":8,"width":220,"height":124,"cols":2,"rows":2,"interval":1.5,"quality":"low","images":["b.jpg"]}
        ]""")
        val board = requireNotNull(VideoStoryboard.from(url, sets))
        assertEquals(160, board.frameWidth)
        assertEquals(160, board.frameAt(1_500)?.left)
        assertNull(board.frameAt(6_000)) // Missing sheet: never reuse a previous frame.
        assertEquals(220, VideoStoryboard.from(url, sets.drop(1))?.frameWidth)
    }

    @Test fun `reject invalid intervals dimensions and URLs without crashing`() {
        for (interval in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            assertNull(VideoStoryboard.from(url, listOf(info(interval = interval))))
        }
        assertNull(VideoStoryboard.from(url, listOf(info(cols = Int.MAX_VALUE))))
        assertNull(VideoStoryboard.from(url, emptyList()))
        assertNull(VideoStoryboard.from(url, listOf(VideoStoryboard.Info(1, 160, 90, 1, 1, 1.0, images = listOf("file:///tmp/a")))))
    }
}
