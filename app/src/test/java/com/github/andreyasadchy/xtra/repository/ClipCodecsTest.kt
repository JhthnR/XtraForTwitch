package com.github.andreyasadchy.xtra.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipCodecsTest {
    @Test fun `clip requests ask only for codecs the playback preference allows`() {
        assertEquals(listOf("AVC", "HEVC", "AV1"), clipCodecs("av1,h265,h264"))
        assertEquals(listOf("AVC", "HEVC"), clipCodecs("h265, H264"))
        assertEquals(listOf("AVC"), clipCodecs("h264"))
        assertEquals(emptyList<String>(), clipCodecs(null))
        assertEquals(emptyList<String>(), clipCodecs("vp9"))
    }
}
