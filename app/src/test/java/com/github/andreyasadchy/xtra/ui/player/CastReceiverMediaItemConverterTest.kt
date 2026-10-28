package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.android.gms.cast.MediaInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class CastReceiverMediaItemConverterTest {
    private val converter = CastReceiverMediaItemConverter()
    private val live = MediaItem.Builder()
        .setUri("https://usher.example/live.m3u8")
        .setMimeType("application/x-mpegURL")
        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build())
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Stream").build())
        .build()

    @Test fun `receiver loads the variant while the session keeps the local identity`() {
        val media = converter.toMediaQueueItem(live.withReceiverUri(Uri.parse("https://cdn.example/720p60.m3u8"))).media!!
        assertEquals("https://cdn.example/720p60.m3u8", media.contentUrl)
        assertEquals("https://usher.example/live.m3u8", media.contentId)
        assertEquals("application/x-mpegURL", media.contentType)
        assertEquals(MediaInfo.STREAM_TYPE_LIVE, media.streamType)
        val restored = converter.toMediaItem(converter.toMediaQueueItem(live.withReceiverUri(Uri.parse("https://cdn.example/720p60.m3u8"))))
        assertEquals(Uri.parse("https://usher.example/live.m3u8"), restored.localConfiguration?.uri)
    }

    @Test fun `untagged media is sent unchanged`() {
        val clip = MediaItem.Builder().setUri("https://clips.example/clip.mp4").setMimeType("video/mp4").build()
        val media = converter.toMediaQueueItem(clip).media!!
        assertEquals("https://clips.example/clip.mp4", media.contentUrl)
        assertEquals("video/mp4", media.contentType)
    }

    @Test fun `load acknowledgement token preserves default converter data and local identity`() {
        val tagged = live.buildUpon().setTag(CastReceiverUri(Uri.parse("https://cdn.example/live.m3u8"), "request-123")).build()
        val queue = converter.toMediaQueueItem(tagged)
        assertEquals("request-123", queue.media!!.customData!!.getString(CAST_LOAD_REQUEST))
        assertEquals(live.localConfiguration!!.uri, converter.toMediaItem(queue).localConfiguration!!.uri)
        assertEquals("https://cdn.example/live.m3u8", queue.media!!.contentUrl)
    }
}
