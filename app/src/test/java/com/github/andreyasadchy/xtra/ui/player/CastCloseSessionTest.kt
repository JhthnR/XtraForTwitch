package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.Futures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class CastCloseSessionTest {
    private fun fragment(controller: MediaController): Media3Fragment {
        whenever(controller.applicationLooper).thenReturn(Looper.getMainLooper())
        return Media3Fragment().apply {
            PlayerFragment::class.java.getDeclaredField("videoType").apply { isAccessible = true }
                .set(this, PlayerFragment.STREAM)
            Media3Fragment::class.java.getDeclaredField("controllerFuture").apply { isAccessible = true }
                .set(this, Futures.immediateFuture(controller))
        }
    }

    @Test fun `explicit close stops media and ends the receiver session`() {
        val controller = mock<MediaController>()
        fragment(controller).closeSession()
        inOrder(controller) {
            verify(controller).pause()
            verify(controller).stop()
            verify(controller).clearMediaItems()
            verify(controller).sendCustomCommand(eq(SessionCommand(PlaybackService.END_CAST_SESSION, Bundle.EMPTY)), any())
        }
    }

    @Test fun `replacement stops old media while retaining the receiver route`() {
        val controller = mock<MediaController>()
        fragment(controller).close()
        verify(controller).stop()
        verify(controller).clearMediaItems()
        verify(controller, never()).sendCustomCommand(eq(SessionCommand(PlaybackService.END_CAST_SESSION, Bundle.EMPTY)), any())
    }
    private fun casting(controller: MediaController, remote: Boolean = true, item: MediaItem? = MediaItem.fromUri("https://example.com/vod.m3u8")) {
        whenever(controller.deviceInfo).thenReturn(
            DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build())
        whenever(controller.currentMediaItem).thenReturn(item)
    }

    private fun verifyClosed(controller: MediaController) {
        verify(controller).stop()
        verify(controller).clearMediaItems()
        verify(controller).sendCustomCommand(eq(SessionCommand(PlaybackService.END_CAST_SESSION, Bundle.EMPTY)), any())
    }

    @Test fun `app exit with keep casting leaves receiver playback running`() {
        val controller = mock<MediaController>()
        casting(controller)
        fragment(controller).closeOnExit(keepCasting = true)
        verify(controller, never()).pause()
        verify(controller, never()).stop()
        verify(controller, never()).clearMediaItems()
        verify(controller, never()).sendCustomCommand(eq(SessionCommand(PlaybackService.END_CAST_SESSION, Bundle.EMPTY)), any())
        verify(controller).release()
    }

    @Test fun `app exit without the option still ends the receiver session`() {
        val controller = mock<MediaController>()
        casting(controller)
        fragment(controller).closeOnExit(keepCasting = false)
        verifyClosed(controller)
    }

    @Test fun `app exit with the option closes local playback normally`() {
        val controller = mock<MediaController>()
        casting(controller, remote = false)
        fragment(controller).closeOnExit(keepCasting = true)
        verifyClosed(controller)
    }

    @Test fun `app exit with the option closes an empty receiver session`() {
        val controller = mock<MediaController>()
        casting(controller, item = null)
        fragment(controller).closeOnExit(keepCasting = true)
        verifyClosed(controller)
    }

    @Test fun `service survives task removal only for opted-in receiver playback`() {
        fun player(remote: Boolean, item: MediaItem?) = mock<Player>().also {
            whenever(it.deviceInfo).thenReturn(
                DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build())
            whenever(it.currentMediaItem).thenReturn(item)
        }
        val item = MediaItem.fromUri("https://example.com/vod.m3u8")
        assertTrue(PlaybackService.keepsCastingAfterExit(true, player(true, item)))
        assertFalse(PlaybackService.keepsCastingAfterExit(false, player(true, item)))
        assertFalse(PlaybackService.keepsCastingAfterExit(true, player(false, item)))
        assertFalse(PlaybackService.keepsCastingAfterExit(true, player(true, null)))
    }

    @Test fun `rejected download clears started UI and releases controller without closing receiver`() {
        val controller = mock<MediaController>()
        whenever(controller.applicationLooper).thenReturn(Looper.getMainLooper())
        val fragment = mock<Media3Fragment>(defaultAnswer = Mockito.CALLS_REAL_METHODS,
            useConstructor = UseConstructor.parameterless())
        val activity = mock<MainActivity>()
        val model = mock<PlayerViewModel>()
        val context = RuntimeEnvironment.getApplication()
        doReturn(context).whenever(fragment).requireContext()
        doReturn(activity).whenever(fragment).activity
        ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(model))
        ReflectionHelpers.setField(fragment, "controllerFuture", Futures.immediateFuture(controller))
        Media3Fragment::class.java.getDeclaredMethod("rejectSourceStart", String::class.java, Integer.TYPE).apply {
            isAccessible = true
        }.invoke(fragment, PlaybackService.START_OFFLINE_VIDEO, androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED)
        verify(model).started = false
        verify(activity).discardRejectedPlayer(fragment)
        verify(controller).release()
        verify(controller, never()).pause()
        verify(controller, never()).stop()
        verify(controller, never()).clearMediaItems()
        verify(controller, never()).currentPosition
        assertTrue(ShadowToast.getTextOfLatestToast() == context.getString(R.string.cast_disconnect_download))
    }
}
