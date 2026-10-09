package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.net.NetworkCapabilities
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [23, 28])
class PlayerQualityNetworkProfileTest {
    private fun network(wifi: Boolean = false, cellular: Boolean = false, unmetered: Boolean = false, vpn: Boolean = false): NetworkCapabilities =
        Mockito.mock(NetworkCapabilities::class.java).apply {
            Mockito.`when`(hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(wifi)
            Mockito.`when`(hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(cellular)
            Mockito.`when`(hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(vpn)
            Mockito.`when`(hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).thenReturn(unmetered)
        }

    @Test fun `metered wifi is distinct and cellular wins on mixed transports`() {
        assertEquals(PlayerQualityNetworkProfile.METERED_WIFI, PlayerQualityNetworkProfile.from(network(wifi = true)))
        assertEquals(PlayerQualityNetworkProfile.WIFI, PlayerQualityNetworkProfile.from(network(wifi = true, unmetered = true)))
        assertEquals(PlayerQualityNetworkProfile.CELLULAR, PlayerQualityNetworkProfile.from(network(wifi = true, cellular = true, unmetered = true)))
        assertEquals(PlayerQualityNetworkProfile.CELLULAR, PlayerQualityNetworkProfile.from(network(cellular = true)))
    }

    @Test fun `VPN follows exposed underlying transport while missing or other networks keep old default`() {
        assertEquals(PlayerQualityNetworkProfile.METERED_WIFI, PlayerQualityNetworkProfile.from(network(wifi = true, vpn = true)))
        assertEquals(PlayerQualityNetworkProfile.CELLULAR, PlayerQualityNetworkProfile.from(network(cellular = true, vpn = true)))
        assertEquals(PlayerQualityNetworkProfile.WIFI, PlayerQualityNetworkProfile.from(network(vpn = true)))
        assertEquals(PlayerQualityNetworkProfile.WIFI, PlayerQualityNetworkProfile.from(null))
        assertEquals(PlayerQualityNetworkProfile.WIFI, PlayerQualityNetworkProfile.from(network()))
    }

    @Test fun `metered preset inherits mobile by default and explicit choices override only metered wifi`() {
        val prefs = RuntimeEnvironment.getApplication().prefs()
        prefs.edit().clear().putString(C.PLAYER_DEFAULTQUALITY, "1080p60")
            .putString(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "360").commit()
        assertEquals("1080p60", PlayerQualityNetworkProfile.WIFI.qualityPreference(prefs))
        assertEquals("360", PlayerQualityNetworkProfile.CELLULAR.qualityPreference(prefs))
        assertEquals("360", PlayerQualityNetworkProfile.METERED_WIFI.qualityPreference(prefs))
        prefs.edit().putString(C.PLAYER_DEFAULT_METERED_WIFI_QUALITY, "720").commit()
        assertEquals("720", PlayerQualityNetworkProfile.METERED_WIFI.qualityPreference(prefs))
        assertEquals("360", PlayerQualityNetworkProfile.CELLULAR.qualityPreference(prefs))
        prefs.edit().putString(C.PLAYER_DEFAULT_METERED_WIFI_QUALITY, "saved").commit()
        assertEquals("saved", PlayerQualityNetworkProfile.METERED_WIFI.qualityPreference(prefs))
        prefs.edit().putString(C.PLAYER_DEFAULT_METERED_WIFI_QUALITY, PlayerQualityNetworkProfile.USE_MOBILE_DATA_QUALITY)
            .putString(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "audio_only").commit()
        assertEquals("audio_only", PlayerQualityNetworkProfile.METERED_WIFI.qualityPreference(prefs))
    }

    @Test fun `metered preset offers all existing quality values plus mobile inheritance`() {
        val resources = RuntimeEnvironment.getApplication().resources
        val entries = resources.getStringArray(R.array.playerMeteredWifiQualityEntries)
        val values = resources.getStringArray(R.array.playerMeteredWifiQualityValues)
        assertEquals(entries.size, values.size)
        assertEquals("mobile_data", values.first())
        assertArrayEquals(resources.getStringArray(R.array.playerQualityValues), values.drop(1).toTypedArray())
        assertTrue(values.contains("chat_only"))
    }
    @Test fun `network changes preserve explicit choices but not policy-chosen audio`() {
        assertTrue(shouldReapplyNetworkQuality(true, false))
        assertFalse(shouldReapplyNetworkQuality(true, true))
        assertFalse(shouldReapplyNetworkQuality(false, false))
    }
    @Test fun `saved default persists codec bitrate without credentials and supports old name-only prefs`() {
        val prefs = RuntimeEnvironment.getApplication().prefs()
        prefs.edit().clear().putString(C.PLAYER_QUALITY, "1080p60").commit()
        val high = VideoQuality("1080p60", "avc1", "https://example.com/high?token=private", 6000000)
        val low = high.copy(url = "https://example.com/low?token=private", bitrate = 3000000)
        assertSame(high, PlayerQualityPreferences.resolve(prefs, listOf(high, low)))
        PlayerQualityPreferences.save(prefs, low)
        assertSame(low, PlayerQualityPreferences.resolve(prefs, listOf(high, low)))
        assertFalse(prefs.all.values.any { it.toString().contains("token=") })
        PlayerQualityPreferences.save(prefs, VideoQuality("auto"))
        assertFalse(prefs.contains(C.PLAYER_QUALITY_CODECS))
        assertFalse(prefs.contains(C.PLAYER_QUALITY_BITRATE))
        assertEquals("auto", PlayerQualityPreferences.resolve(prefs, listOf(VideoQuality("auto"), high))?.name)
    }
}
