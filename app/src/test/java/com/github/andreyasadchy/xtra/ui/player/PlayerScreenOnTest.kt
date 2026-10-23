package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.core.content.edit
import androidx.media3.common.DeviceInfo
import androidx.media3.session.MediaController
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.common.util.concurrent.Futures
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class PlayerScreenOnTest {
    @Test fun `return from remote buffering honors keep awake while paused without waking remote playback`() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        val player = mock<MediaController>()
        whenever(player.applicationLooper).thenReturn(Looper.getMainLooper())
        val fragment = spy(Media3Fragment())
        doReturn(context).whenever(fragment).context
        doReturn(view).whenever(fragment).view
        doReturn(true).whenever(fragment).canEnterPictureInPicture()
        ReflectionHelpers.setField(fragment, "controllerFuture", Futures.immediateFuture(player))
        context.prefs().edit { putBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, true) }
        whenever(player.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())
        ReflectionHelpers.callInstanceMethod<Unit>(fragment, "updateKeepScreenOn")
        assertFalse(view.keepScreenOn)
        whenever(player.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        whenever(player.isPlaying).thenReturn(false)
        ReflectionHelpers.callInstanceMethod<Unit>(fragment, "updateKeepScreenOn")
        assertTrue(view.keepScreenOn)
        context.prefs().edit { putBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false) }
        ReflectionHelpers.callInstanceMethod<Unit>(fragment, "updateKeepScreenOn")
        assertFalse(view.keepScreenOn)
        whenever(player.isPlaying).thenReturn(true)
        ReflectionHelpers.callInstanceMethod<Unit>(fragment, "updateKeepScreenOn")
        assertTrue(view.keepScreenOn)
    }
}
