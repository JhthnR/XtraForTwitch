package com.github.andreyasadchy.xtra.ui.main

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
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
class MainNetworkValidationTest {
    @Test fun `validation after availability requests a fresh fragment network check and destruction cancels it`() {
        // Exercise the real activity callback without onCreate, auth, workers or a live account.
        val activity = mock<MainActivity>(defaultAnswer = Mockito.CALLS_REAL_METHODS,
            useConstructor = UseConstructor.parameterless())
        val model = mock<MainViewModel>()
        val checks = MutableStateFlow(false)
        whenever(model.checkNetworkStatus).thenReturn(checks)
        ReflectionHelpers.setField(activity, "viewModel\$delegate", lazyOf(model))
        doReturn(mock<ConnectivityManager>()).whenever(activity).getSystemService(Context.CONNECTIVITY_SERVICE)
        val lifecycle = LifecycleRegistry(activity)
        doReturn(lifecycle).whenever(activity).lifecycle
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val callback = ReflectionHelpers.getField<ConnectivityManager.NetworkCallback>(activity, "networkCallback")
        val network = mock<Network>()
        callback.onAvailable(network)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(checks.value)
        checks.value = false // Activity consumed the early, not-yet-validated availability.
        val validated = mock<NetworkCapabilities>()
        whenever(validated.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
        callback.onCapabilitiesChanged(network, validated)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Later validation must notify fragment/chat restoration", checks.value)
        checks.value = false
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        callback.onCapabilitiesChanged(network, validated)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("Destroyed activity must not emit a late check", checks.value)
    }
}
