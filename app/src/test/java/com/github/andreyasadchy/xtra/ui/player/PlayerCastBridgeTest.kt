package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import androidx.media3.cast.CastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.*
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
class PlayerCastBridgeTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test fun `setting changes replace only the idle route while keeping the session player stable`() {
        val locals = mutableListOf<ExoPlayer>()
        var casts = 0
        val bridge = PlayerCastBridge(context, false,
            createLocal = { spy(ExoPlayer.Builder(context).build()).also(locals::add) },
            endCurrentSession = {}, createCast = { local, _ -> casts++; ForwardingPlayer(local) })
        val sessionPlayer = bridge.player
        try {
            assertFalse(bridge.castAvailable)
            assertEquals(0, casts)
            sessionPlayer.setMediaItem(MediaItem.fromUri("https://example.com/old.m3u8"))
            sessionPlayer.playWhenReady = true
            clearInvocations(locals[0])
            bridge.configure(true)
            assertSame(sessionPlayer, bridge.player)
            assertTrue(bridge.castAvailable)
            assertNull(sessionPlayer.currentMediaItem)
            assertFalse(sessionPlayer.playWhenReady)
            inOrder(locals[0]) {
                verify(locals[0]).pause()
                verify(locals[0]).stop()
                verify(locals[0]).clearMediaItems()
                verify(locals[0]).release()
            }
            bridge.configure(true)
            assertEquals(2, locals.size)
            bridge.configure(false)
            assertFalse(bridge.castAvailable)
            assertSame(sessionPlayer, bridge.player)
            assertEquals(3, locals.size)
            verify(locals[1]).release()
            assertEquals(1, casts)
        } finally {
            bridge.dispose()
            sessionPlayer.release()
        }
    }

    @Test fun `explicit disconnect cleans an existing SDK session even with a local-only bridge`() {
        var ended = 0
        val bridge = PlayerCastBridge(context, false, { ExoPlayer.Builder(context).build() },
            endCurrentSession = { ended++ }, createCast = { _, _ -> error("Cast must remain lazy") })
        try {
            bridge.endSession()
            assertEquals(1, ended)
        } finally { bridge.dispose(); bridge.player.release() }
    }

    @Test fun `retired Cast transfer callbacks cannot revive an old source`() {
        lateinit var retired: CastPlayer.TransferCallback
        val bridge = PlayerCastBridge(context, true, { ExoPlayer.Builder(context).build() },
            endCurrentSession = {}, createCast = { local, callback ->
                retired = callback
                ForwardingPlayer(local)
            })
        try {
            bridge.configure(false)
            val from = mock<Player>()
            val to = mock<Player>()
            retired.transferState(from, to)
            verifyNoInteractions(from, to)
        } finally { bridge.dispose(); bridge.player.release() }
    }

    @Test fun `source defaults affect local controls and leave receiver volume and speed unchanged`() {
        val local = mock<Player>()
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        local.applyLocalPlaybackDefaults(0.2f, 1.25f)
        verify(local).setVolume(0.2f)
        verify(local).setPlaybackSpeed(1.25f)
        val remote = mock<Player>()
        whenever(remote.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())
        remote.applyLocalPlaybackDefaults(0.2f, 1.25f)
        verify(remote, never()).setVolume(any())
        verify(remote, never()).setPlaybackSpeed(any())
    }

    @Test fun `unavailable Cast SDK reports local capability and keeps playback usable`() {
        val bridge = PlayerCastBridge(context, true, { ExoPlayer.Builder(context).build() },
            endCurrentSession = {}, createCast = { _, _ -> throw IllegalStateException("No Cast SDK") })
        try {
            assertFalse(bridge.castAvailable)
            val item = MediaItem.fromUri("https://example.com/local.m3u8")
            bridge.player.setMediaItem(item)
            assertEquals(item, bridge.player.currentMediaItem)
        } finally { bridge.dispose(); bridge.player.release() }
    }
}
