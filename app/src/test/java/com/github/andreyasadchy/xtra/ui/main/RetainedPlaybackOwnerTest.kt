package com.github.andreyasadchy.xtra.ui.main

import android.app.Application
import androidx.core.content.edit
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.player.PlaybackSessionOwner
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class RetainedPlaybackOwnerTest {
    @Test fun `reopened activity rejects every alternate backend entry while service owns media`() {
        val app = RuntimeEnvironment.getApplication()
        val owner = mock<Player>()
        var remote = true
        whenever(owner.currentMediaItem).thenReturn(MediaItem.fromUri("https://example.com/old.m3u8"))
        whenever(owner.deviceInfo).thenAnswer {
            DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build()
        }
        PlaybackSessionOwner.register(owner)
        try {
            for (route in listOf(true, false)) {
                remote = route // Receiver disconnect must not permit a second local owner.
                for (backend in listOf("MediaPlayer", "customExo")) {
                    val activity = Robolectric.buildActivity(MainActivity::class.java).get()
                    val prefs = app.prefs()
                    prefs.edit {
                        putString(C.PLAYER, if (backend == "MediaPlayer") "MediaPlayer" else "ExoPlayer")
                        putBoolean(C.DEBUG_USE_CUSTOM_PLAYBACK_SERVICE, backend == "customExo")
                    }
                    ReflectionHelpers.setField(activity, "prefs", prefs)
                    val actions: List<() -> Unit> = listOf(
                        { activity.startStream(Stream(channelId = "1", channelLogin = "test")) },
                        { activity.startVideo(Video(id = "123"), null) },
                        { activity.startClip(Clip(id = "clip")) },
                        { activity.startOfflineVideo(OfflineVideo(url = "file:///storage/video.mp4")) },
                    )
                    for ((index, action) in actions.withIndex()) {
                        action()
                        assertNull("$backend route=$route entry=$index", activity.playerFragment)
                        val message = if (remote && index == 3) R.string.cast_disconnect_download else R.string.player_session_in_use
                        assertEquals(app.getString(message), ShadowToast.getTextOfLatestToast())
                    }
                }
            }
            verify(owner, never()).pause()
            verify(owner, never()).stop()
            verify(owner, never()).clearMediaItems()
        } finally {
            PlaybackSessionOwner.unregister(owner)
        }
    }

    @Test fun `default backend download is rejected before creating unrelated playback UI`() {
        val app = RuntimeEnvironment.getApplication()
        val owner = mock<Player>()
        whenever(owner.currentMediaItem).thenReturn(MediaItem.fromUri("https://example.com/old.m3u8"))
        whenever(owner.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())
        PlaybackSessionOwner.register(owner)
        try {
            val activity = Robolectric.buildActivity(MainActivity::class.java).get()
            activity.startOfflineVideo(OfflineVideo(url = "file:///storage/download.mp4"))
            assertNull(activity.playerFragment)
            assertEquals(app.getString(R.string.cast_disconnect_download), ShadowToast.getTextOfLatestToast())
            verify(owner, never()).setMediaItem(any())
            verify(owner, never()).stop()
        } finally {
            PlaybackSessionOwner.unregister(owner)
        }
    }

    @Test fun `old teardown cannot clear a newer service owner and empty media releases guard`() {
        val old = mock<Player>()
        val newer = mock<Player>()
        whenever(newer.currentMediaItem).thenReturn(MediaItem.fromUri("https://example.com/new.m3u8"))
        PlaybackSessionOwner.register(old)
        PlaybackSessionOwner.register(newer)
        try {
            PlaybackSessionOwner.unregister(old)
            assertTrue(PlaybackSessionOwner.hasMedia())
            whenever(newer.currentMediaItem).thenReturn(null)
            assertFalse(PlaybackSessionOwner.hasMedia())
        } finally {
            PlaybackSessionOwner.unregister(newer)
        }
        assertFalse(PlaybackSessionOwner.hasMedia())
    }
}
