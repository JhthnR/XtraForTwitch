package com.github.andreyasadchy.xtra.ui.chat

import android.content.Context
import android.content.SharedPreferences
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ChannelPointRewardsSessionProvider @Inject constructor(@ApplicationContext private val context: Context) {
    internal fun current(channelId: String?, channelLogin: String?) = ChannelPointRewardsSession(
        context.tokenPrefs().getString(C.USER_ID, null), channelId, channelLogin,
        context.prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
        if (context.prefs().getBoolean(C.ENABLE_INTEGRITY, false)) TwitchApiHelper.getGQLHeaders(context, true)
        else webHeaders(context.prefs(), context.tokenPrefs()),
    )

    companion object {
        /** Private reward operations require the web token/client pair, never the TV or Helix pair. */
        internal fun webHeaders(preferences: SharedPreferences, tokens: SharedPreferences) = buildMap {
            preferences.getString(C.GQL_CLIENT_ID_WEB, "kimne78kx3ncx6brgo4mv6wki5h1ko")?.takeIf { it.isNotBlank() }?.let { put(C.HEADER_CLIENT_ID, it) }
            tokens.getString(C.GQL_TOKEN_WEB, null)?.takeIf { it.isNotBlank() }?.let { put(C.HEADER_TOKEN, TwitchApiHelper.addTokenPrefixGQL(it)) }
        }
    }
}
