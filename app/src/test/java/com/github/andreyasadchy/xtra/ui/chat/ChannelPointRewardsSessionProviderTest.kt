package com.github.andreyasadchy.xtra.ui.chat

import android.content.SharedPreferences
import com.github.andreyasadchy.xtra.util.C
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class ChannelPointRewardsSessionProviderTest {
    @Test fun `web only account uses web client and token`() {
        val preferences: SharedPreferences = mock()
        val tokens: SharedPreferences = mock()
        whenever(preferences.getString(C.GQL_CLIENT_ID_WEB, "kimne78kx3ncx6brgo4mv6wki5h1ko")).thenReturn("web-client")
        whenever(tokens.getString(C.GQL_TOKEN_WEB, null)).thenReturn("web-token")
        val headers = ChannelPointRewardsSessionProvider.webHeaders(preferences, tokens)
        assertEquals("web-client", headers[C.HEADER_CLIENT_ID])
        assertEquals("OAuth web-token", headers[C.HEADER_TOKEN])
        assertTrue(ChannelPointRewardsSession("user", "channel", "login", "OkHttp", headers).canRedeem)
        verifyNoMoreInteractionsAfterReads(preferences, tokens)
    }
    @Test fun `TV and Helix tokens cannot masquerade as web authorization`() {
        val preferences: SharedPreferences = mock()
        val tokens: SharedPreferences = mock()
        whenever(preferences.getString(C.GQL_CLIENT_ID_WEB, "kimne78kx3ncx6brgo4mv6wki5h1ko")).thenReturn("web-client")
        val headers = ChannelPointRewardsSessionProvider.webHeaders(preferences, tokens)
        assertNull(headers[C.HEADER_TOKEN])
        assertFalse(ChannelPointRewardsSession("user", "channel", "login", "OkHttp", headers).canRedeem)
        verifyNoMoreInteractionsAfterReads(preferences, tokens)
    }
    private fun verifyNoMoreInteractionsAfterReads(preferences: SharedPreferences, tokens: SharedPreferences) {
        verify(preferences).getString(C.GQL_CLIENT_ID_WEB, "kimne78kx3ncx6brgo4mv6wki5h1ko")
        verify(tokens).getString(C.GQL_TOKEN_WEB, null)
        verifyNoMoreInteractions(preferences, tokens)
    }
}
