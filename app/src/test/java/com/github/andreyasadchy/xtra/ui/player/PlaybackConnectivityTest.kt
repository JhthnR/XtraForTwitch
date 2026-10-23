package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackConnectivityTest {
    @Test fun `validated active network wins over old handover callbacks and shutdown drops late notifications`() {
        val connectivity = mock<ConnectivityManager>()
        val service = mock<PlaybackService>(defaultAnswer = Mockito.CALLS_REAL_METHODS,
            useConstructor = UseConstructor.parameterless())
        val recovery = mock<LivePlaybackRecovery>()
        doReturn(connectivity).whenever(service).getSystemService(Context.CONNECTIVITY_SERVICE)
        ReflectionHelpers.setField(service, "liveRecovery", recovery)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "watchRecoveryConnectivity")
        val callback = argumentCaptor<ConnectivityManager.NetworkCallback>().run {
            if (Build.VERSION.SDK_INT >= 24) verify(connectivity).registerDefaultNetworkCallback(capture())
            else {
                val request = argumentCaptor<NetworkRequest>()
                verify(connectivity).registerNetworkCallback(request.capture(), capture())
                val caps = ReflectionHelpers.getField<NetworkCapabilities>(request.firstValue, "networkCapabilities")
                assertTrue(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                assertFalse("VPN updates must not be excluded on API 23", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
            }
            firstValue
        }
        val active = mock<Network>()
        val old = mock<Network>()
        val validated = mock<NetworkCapabilities>().also {
            whenever(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
            whenever(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
        }
        whenever(connectivity.activeNetwork).thenReturn(active)
        whenever(connectivity.getNetworkCapabilities(active)).thenReturn(validated)
        callback.onLost(old)
        shadowOf(Looper.getMainLooper()).idle()
        verify(recovery).onConnectivityChanged(true)
        verify(connectivity, never()).getNetworkCapabilities(old)
        val unvalidated = mock<NetworkCapabilities>().also {
            whenever(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        }
        whenever(connectivity.getNetworkCapabilities(active)).thenReturn(unvalidated)
        callback.onCapabilitiesChanged(active, validated)
        shadowOf(Looper.getMainLooper()).idle()
        verify(recovery).onConnectivityChanged(false)
        clearInvocations(recovery)
        callback.onAvailable(active)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "stopRecoveryConnectivity")
        verify(connectivity).unregisterNetworkCallback(callback)
        callback.onLost(old)
        shadowOf(Looper.getMainLooper()).idle()
        verifyNoInteractions(recovery)
    }
}
