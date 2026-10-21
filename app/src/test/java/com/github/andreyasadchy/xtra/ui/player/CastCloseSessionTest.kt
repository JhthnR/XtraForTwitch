package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.Futures
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

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
}
