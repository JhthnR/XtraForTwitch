package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class CastPlaybackHandoffTest {
    private val local = mock<ExoPlayer>()
    private val remote = mock<Player>().also {
        whenever(it.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())
        whenever(it.playbackParameters).thenReturn(PlaybackParameters(1.25f))
        whenever(it.currentPosition).thenReturn(42_000L)
        whenever(it.playWhenReady).thenReturn(false)
    }
    private val handoff = CastPlaybackHandoff(local)
    private fun source(url: String = "https://example.com/vod.m3u8", live: Boolean = false): MediaSource {
        val item = MediaItem.Builder().setUri(url).setMimeType("application/x-mpegURL").apply {
            if (live) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build())
        }.build()
        return mock<MediaSource>().also { whenever(it.mediaItem).thenReturn(item) }
    }

    @Test fun `local playback keeps its exact configured media source`() {
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        val source = source()
        handoff.setSource(local, source)
        verify(local).setMediaSource(source)
        verify(local, never()).setMediaItem(any())
    }

    @Test fun `new remote content is sent to the active receiver instead of starting local video`() {
        val source = source()
        handoff.setSource(remote, source)
        verify(remote).setMediaItem(source.mediaItem)
        verifyNoInteractions(local)
    }

    @Test fun `selected quality returns through the configured source factory`() {
        val master = source()
        val selected = source("https://example.com/720p.m3u8")
        val masterItem = master.mediaItem
        val selectedItem = selected.mediaItem
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(masterItem)).thenReturn(master)
        whenever(factory.createMediaSource(selectedItem)).thenReturn(selected)
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        whenever(local.currentMediaItem).thenReturn(selectedItem)
        whenever(remote.currentMediaItem).thenReturn(selectedItem)
        var transfers = 0
        val handoff = CastPlaybackHandoff(local) { _, _ -> transfers++ }
        handoff.setSource(local, factory, masterItem)
        handoff.transfer(local, remote) { fail("Online quality should transfer") }
        handoff.transfer(remote, local) { fail("Return should not reject") }
        assertEquals(1, transfers)
        verify(factory).createMediaSource(selectedItem)
        verify(local).setMediaSource(selected, 42_000L)
        verify(local).setPlayWhenReady(false)
    }

    @Test fun `returning VoD reuses parser and HTTP source with receiver position pause and speed`() {
        val source = source()
        handoff.setSource(remote, source)
        val item = source.mediaItem
        whenever(remote.currentMediaItem).thenReturn(item)
        assertTrue(handoff.restoreFrom(remote))
        verify(local).setMediaSource(source, 42_000L)
        verify(local).setPlaybackParameters(PlaybackParameters(1.25f))
        verify(local).prepare()
        verify(local).setPlayWhenReady(false)
    }

    @Test fun `returning live uses live edge rather than a receiver window offset`() {
        val source = source(live = true)
        handoff.setSource(remote, source)
        val item = source.mediaItem
        whenever(remote.currentMediaItem).thenReturn(item)
        whenever(remote.playWhenReady).thenReturn(true)
        assertTrue(handoff.restoreFrom(remote))
        verify(local).setMediaSource(source, C.TIME_UNSET)
        verify(local).setPlayWhenReady(true)
    }

    @Test fun `old receiver media cannot resurrect a replaced stream`() {
        val old = source("https://example.com/old.m3u8")
        handoff.setSource(remote, old)
        handoff.setSource(remote, source("https://example.com/new.m3u8"))
        val item = old.mediaItem
        whenever(remote.currentMediaItem).thenReturn(item)
        assertFalse(handoff.restoreFrom(remote))
        verifyNoInteractions(local)
    }

    @Test fun `cleared session cannot restore a stale source`() {
        val source = source()
        handoff.setSource(remote, source)
        val item = source.mediaItem
        whenever(remote.currentMediaItem).thenReturn(item)
        handoff.clear()
        assertFalse(handoff.restoreFrom(remote))
        verifyNoInteractions(local)
    }

    @Test fun `receiver URLs require reachable online media rather than phone files or loopback`() {
        assertTrue(CastPlaybackHandoff.canCast(MediaItem.fromUri("https://example.com/live.m3u8")))
        assertTrue(CastPlaybackHandoff.canCast(MediaItem.fromUri("http://8.8.8.8/live")))
        assertTrue(CastPlaybackHandoff.canCast(MediaItem.fromUri("http://[2001:4860:4860::8888]/live")))
        for (uri in listOf("file:///storage/video.mp4", "content://downloads/1", "http://LOCALHOST/live", "http://localhost/live", "http://127.0.0.1/live", "http://[::1]/live",
            "http://[0:0:0:0:0:0:0:1]/live", "http://[::ffff:127.0.0.1]/live", "http://[::]/live", "http://2130706433/live",
            "http://999.10.0.1/live", "http://256.1.1.1/live", "http://999999999/live")) {
            assertFalse(uri, CastPlaybackHandoff.canCast(MediaItem.fromUri(uri)))
        }
        assertFalse(CastPlaybackHandoff.canCast(null))
    }

    @Test fun `rejected phone download returns at the same paused position without loading the receiver`() {
        val item = MediaItem.fromUri("file:///storage/video.mp4")
        whenever(local.currentMediaItem).thenReturn(item)
        whenever(local.currentPosition).thenReturn(9_500L)
        whenever(local.playWhenReady).thenReturn(false)
        whenever(local.playbackParameters).thenReturn(PlaybackParameters(1.5f))
        var rejected = 0
        handoff.transfer(local, remote) { rejected++ }
        assertEquals(1, rejected)
        verify(remote).clearMediaItems()
        verify(remote, never()).setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>())
        handoff.transfer(remote, local) { fail("Return to local must not reject again") }
        verify(local).setMediaItem(item, 9_500L)
        verify(local).setPlaybackParameters(PlaybackParameters(1.5f))
        verify(local).setPlayWhenReady(false)
        verify(local).prepare()
    }

    @Test fun `empty session does not show an unsupported media error`() {
        handoff.transfer(local, remote) { fail("Nothing was playing") }
        verify(remote).clearMediaItems()
        verifyNoMoreInteractions(remote)
    }

    @Test fun `new source invalidates a rejected local-only snapshot before transfer returns`() {
        val blocked = MediaItem.fromUri("file:///storage/old.mp4")
        whenever(local.currentMediaItem).thenReturn(blocked)
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        whenever(local.playbackParameters).thenReturn(PlaybackParameters(1f))
        handoff.transfer(local, remote) { }
        val next = source("https://example.com/new.m3u8")
        val nextItem = next.mediaItem
        assertTrue(handoff.setSource(local, next))
        whenever(remote.currentMediaItem).thenReturn(nextItem)
        handoff.transfer(remote, local) { fail("Returning from Cast must not reject") }
        verify(local).setMediaSource(next, 42_000L)
        verify(local, never()).setMediaItem(eq(blocked), any<Long>())
    }

    @Test fun `successful new cast transfer invalidates a rejected old snapshot`() {
        val blocked = MediaItem.fromUri("file:///storage/old.mp4")
        val next = source("https://example.com/new.m3u8")
        val nextItem = next.mediaItem
        val handoff = CastPlaybackHandoff(local, transferOnline = { _, _ -> })
        whenever(local.currentMediaItem).thenReturn(blocked)
        whenever(local.playbackParameters).thenReturn(PlaybackParameters(1f))
        handoff.transfer(local, remote) { }
        whenever(local.currentMediaItem).thenReturn(nextItem)
        handoff.transfer(local, remote) { fail("Online transfer must succeed") }
        whenever(remote.currentMediaItem).thenReturn(nextItem)
        handoff.transfer(remote, local) { fail("Return to local must not reject") }
        verify(local).clearMediaItems()
        verify(local, never()).setMediaItem(eq(blocked), any<Long>())
    }

    @Test fun `unowned receiver media is cleared on return rather than paired with stale chat`() {
        whenever(remote.currentMediaItem).thenReturn(MediaItem.fromUri("https://other.example/video"))
        handoff.transfer(remote, local) { fail("Return to local must not reject") }
        verify(local).clearMediaItems()
        verify(local, never()).prepare()
    }

    @Test fun `remote local-only source switches preserve the receiver and retained factory`() {
        val old = source()
        val oldItem = old.mediaItem
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(oldItem)).thenReturn(old)
        val changes = mutableListOf<MediaSource.Factory?>()
        val handoff = CastPlaybackHandoff(local, sourceFactoryChanged = { changes.add(it) })
        assertTrue(handoff.setSource(remote, factory, oldItem))
        clearInvocations(remote)
        for (url in listOf("http://localhost/live", "http://127.0.0.1/live", "http://[::1]/live", "http://[::ffff:127.0.0.1]/live", "http://0.0.0.0/live", "file:///storage/video.mp4")) {
            val replacementFactory = mock<MediaSource.Factory>()
            assertFalse(url, handoff.setSource(remote, replacementFactory, source(url).mediaItem))
            assertFalse(url, handoff.setSource(remote, source(url)))
            verifyNoInteractions(replacementFactory)
        }
        verify(remote, never()).setMediaItem(any())
        assertEquals(listOf(factory), changes)
        whenever(remote.currentMediaItem).thenReturn(oldItem)
        assertTrue(handoff.restoreFrom(remote))
        verify(local).setMediaSource(old, 42_000L)
    }

    @Test fun `phone-local proxy remains playable locally`() {
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        val proxy = source("http://127.0.0.1/live")
        assertTrue(handoff.setSource(local, proxy))
        verify(local).setMediaSource(proxy)
        verifyNoInteractions(remote)
    }

}
