package com.github.andreyasadchy.xtra.repository

import android.content.Context
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.tokenPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Account notification preferences are a cache, never local notification opt-ins. Tokens are not persisted here. */
@Singleton
class LiveNotificationState @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val lock = Any()
    private var revision = 0L
    private val pendingChanges = mutableMapOf<Pair<String, String>, Pair<Long, Boolean>>()
    private val preferences = context.getSharedPreferences("live_notification_follows", Context.MODE_PRIVATE)

    fun accountId(): String? = context.tokenPrefs().getString(C.USER_ID, null)?.takeIf { it.isNotBlank() }

    fun isCurrent(accountId: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>): Boolean =
        accountId == accountId() && gqlHeaders == TwitchApiHelper.getGQLHeaders(context, true) &&
            helixHeaders == TwitchApiHelper.getHelixHeaders(context)

    fun revision(): Long = synchronized(lock) { revision }

    /** Channels last reported by Twitch with notifications enabled for this account. */
    internal fun load(accountId: String): Set<String>? = synchronized(lock) {
        if (preferences.getString("account", null) != accountId) return null
        return preferences.getStringSet("enabled", emptySet()).orEmpty().toSet()
    }

    internal fun saveIfCurrent(accountId: String, expectedRevision: Long, enabledIds: Set<String>): Boolean = synchronized(lock) {
        if (revision != expectedRevision) return false
        save(accountId, enabledIds)
        true
    }

    /** In-memory exclusions become visible with the generation bump, before any IO dispatch. */
    fun invalidateAccount(originatingAccountId: String?, channelId: String, enabled: Boolean): Long? = synchronized(lock) {
        if (originatingAccountId == null || originatingAccountId != accountId()) return null
        revision++
        pendingChanges[originatingAccountId to channelId] = revision to enabled
        revision
    }

    fun accountChannelAllowed(accountId: String?, channelId: String): Boolean = synchronized(lock) {
        accountId == null || pendingChanges[accountId to channelId]?.second != false
    }

    internal fun persistAccountNotificationChanged(originatingAccountId: String?, channelId: String, enabled: Boolean, expectedRevision: Long): Unit = synchronized(lock) {
        if (originatingAccountId == null) return
        val key = originatingAccountId to channelId
        if (pendingChanges[key]?.first != expectedRevision) return
        if (originatingAccountId != accountId()) {
            pendingChanges.remove(key)
            return
        }
        // Also invalidate a poll that began after the first bump but before this durable write.
        revision++
        val previous = load(originatingAccountId).orEmpty()
        save(originatingAccountId, if (enabled) previous + channelId else previous - channelId)
        pendingChanges.remove(key)
    }

    internal fun beginBaseline(accountId: String?): Unit = synchronized(lock) {
        revision++
        if (!preferences.edit().putString("baseline_account", accountId.orEmpty())
                .putBoolean("baseline_pending", true).putStringSet("baseline_completed", emptySet()).commit()
        ) throw IOException("Unable to persist live notification baseline")
    }

    internal fun baselineCompleted(accountId: String?): Set<String>? = synchronized(lock) {
        if (!preferences.getBoolean("baseline_pending", false) ||
            preferences.getString("baseline_account", null) != accountId.orEmpty()
        ) return null
        preferences.getStringSet("baseline_completed", emptySet()).orEmpty().toSet()
    }

    internal fun advanceBaseline(accountId: String?, expectedRevision: Long, completed: Set<String>, complete: Boolean): Boolean = synchronized(lock) {
        if (revision != expectedRevision || preferences.getString("baseline_account", null) != accountId.orEmpty()) return false
        if (!preferences.edit().putStringSet("baseline_completed", if (complete) emptySet() else completed)
                .putBoolean("baseline_pending", !complete).commit()
        ) throw IOException("Unable to persist live notification baseline")
        true
    }

    internal fun save(accountId: String, enabledIds: Set<String>): Unit = synchronized(lock) {
        if (!preferences.edit()
                .putString("account", accountId)
                .putStringSet("enabled", enabledIds)
                .commit()
        ) throw IOException("Unable to persist live notification preferences")
    }
}
