package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ConfiguredMediaSourceFactoryTest {
    private val fallback = mock<MediaSource.Factory>().also {
        whenever(it.supportedTypes).thenReturn(intArrayOf(C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_OTHER))
    }
    private val factory = ConfiguredMediaSourceFactory(fallback)

    @Test fun `real player quality and audio item changes retain the selected HTTP parser factory`() {
        val configured = mock<MediaSource.Factory>()
        val master = MediaItem.fromUri("https://example.com/master.m3u8")
        val quality = master.buildUpon().setUri("https://example.com/720p.m3u8").build()
        val audio = master.buildUpon().setUri("https://example.com/audio.m3u8").build()
        for (item in listOf(master, quality, audio)) prepare(configured, item)
        factory.configure(configured)
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setMediaSourceFactory(factory).build()
        try {
            for (item in listOf(master, quality, audio)) player.setMediaItem(item)
            for (item in listOf(master, quality, audio)) verify(configured).createMediaSource(item)
            verify(fallback, never()).createMediaSource(any())
        } finally { player.release() }
    }

    @Test fun `switching source factories and clearing for downloads never reuses a prior stream factory`() {
        val first = mock<MediaSource.Factory>()
        val second = mock<MediaSource.Factory>()
        val live = MediaItem.fromUri("https://example.com/live.m3u8")
        val vod = MediaItem.fromUri("https://example.com/vod.m3u8")
        val file = MediaItem.fromUri("file:///storage/download.mp4")
        prepare(first, live); prepare(second, vod); prepare(fallback, file)
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setMediaSourceFactory(factory).build()
        try {
            factory.configure(first); player.setMediaItem(live)
            factory.configure(second); player.setMediaItem(vod)
            factory.configure(null); player.setMediaItem(file)
            verify(first).createMediaSource(live)
            verify(second).createMediaSource(vod)
            verify(fallback).createMediaSource(file)
            verify(first, never()).createMediaSource(vod)
            verify(second, never()).createMediaSource(file)
        } finally { player.release() }
    }

    private fun prepare(factory: MediaSource.Factory, item: MediaItem) {
        val source = mock<MediaSource>()
        whenever(source.mediaItem).thenReturn(item)
        whenever(factory.createMediaSource(item)).thenReturn(source)
    }
}
