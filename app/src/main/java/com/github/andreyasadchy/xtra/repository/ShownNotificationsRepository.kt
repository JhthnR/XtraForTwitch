package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.db.ShownNotificationsDao
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.util.C
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ShownNotificationsRepository @Inject constructor(
    private val shownNotificationsDao: ShownNotificationsDao,
    private val state: LiveNotificationState,
) {
    /** Setup baseline: acknowledge currently live streams without posting old live alerts. */
    suspend fun getNewStreams(notificationUsersRepository: NotificationUsersRepository, networkLibrary: String?, gqlHeaders: Map<String, String>, graphQLRepository: GraphQLRepository, helixHeaders: Map<String, String>, helixRepository: HelixRepository): List<Stream> = withContext(Dispatchers.IO) {
        try {
            val accountId = state.accountId()
            if (!state.isCurrent(accountId, gqlHeaders, helixHeaders)) return@withContext emptyList()
            state.beginBaseline(accountId)
            val baseline = mutableListOf<Stream>()
            poll(notificationUsersRepository, networkLibrary, gqlHeaders, graphQLRepository, helixHeaders, helixRepository, onBaseline = { baseline.add(it) }) { true }
            baseline
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // As in 1.3.1, a failed setup poll must not stop the caller from scheduling the worker.
            emptyList()
        }
    }

    /** Returns whether an incomplete fetch or failed delivery needs a WorkManager retry. */
    suspend fun notifyNewStreams(
        notificationUsersRepository: NotificationUsersRepository,
        networkLibrary: String?,
        gqlHeaders: Map<String, String>,
        graphQLRepository: GraphQLRepository,
        helixHeaders: Map<String, String>,
        helixRepository: HelixRepository,
        post: (Stream) -> Boolean,
    ): Boolean = poll(notificationUsersRepository, networkLibrary, gqlHeaders, graphQLRepository, helixHeaders, helixRepository, post = post)

    private suspend fun poll(
        notificationUsersRepository: NotificationUsersRepository,
        networkLibrary: String?,
        gqlHeaders: Map<String, String>,
        graphQLRepository: GraphQLRepository,
        helixHeaders: Map<String, String>,
        helixRepository: HelixRepository,
        onBaseline: (Stream) -> Unit = {},
        post: (Stream) -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        pollMutex.withLock {
            currentCoroutineContext().ensureActive()
            val accountId = state.accountId()
            val revision = state.revision()
            val pendingBaseline = state.pendingBaseline(accountId)
            fun isCurrent() = state.revision() == revision && state.isCurrent(accountId, gqlHeaders, helixHeaders)
            if (!isCurrent()) return@withLock true
            var retry = false
            val hasGqlToken = !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()
            val hasHelixToken = !helixHeaders[C.HEADER_TOKEN].isNullOrBlank()
            val localIds = notificationUsersRepository.loadUsers().mapTo(mutableSetOf()) { it.channelId }.filterTo(mutableSetOf()) { it.isNotBlank() }
            val streams = mutableListOf<Stream>()
            val fetchedIds = mutableSetOf<String>()
            var followed = emptySet<String>()
            var followsResolved = true
            // Without a GQL token no source reports Twitch's notification preferences, so only
            // local opt-ins alert, as in 1.3.1. The channel bell then edits those opt-ins.
            if (accountId != null && hasGqlToken) {
                // Ask only for live follows; paging every follow on each poll scales with follow count.
                val cached = state.load(accountId)
                var live: List<FollowedLiveStream>? = null
                try {
                    live = loadFollowedStreams(networkLibrary, gqlHeaders, graphQLRepository)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    retry = true
                }
                val preferencesReported = live != null
                if (live == null && hasHelixToken) {
                    try {
                        // Helix cannot expose Twitch's per-follow notification preference. Never
                        // turn muted or unknown channels on after a failed authenticated GQL sync.
                        live = loadHelixFollowedStreams(networkLibrary, helixHeaders, accountId, helixRepository).map {
                            FollowedLiveStream(it, it.channelId in cached.orEmpty())
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        retry = true
                    }
                }
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) return@withLock true
                if (live != null) {
                    if (preferencesReported) {
                        // Only live follows report a preference. Keep the rest for later Helix fallbacks.
                        val reported = live.mapNotNull { item -> item.stream.channelId?.let { it to item.notificationsEnabled } }
                        val enabled = cached.orEmpty() - reported.filter { it.second == false }.map { it.first }.toSet() +
                            reported.filter { it.second == true }.map { it.first }
                        if (enabled != cached && !state.saveIfCurrent(accountId, revision, enabled)) return@withLock true
                    }
                    streams.addAll(live.map { it.stream })
                    fetchedIds.addAll(live.mapNotNull { it.stream.channelId })
                    followed = live.filter { it.notificationsEnabled == true }.mapNotNullTo(mutableSetOf()) { it.stream.channelId }
                } else {
                    followsResolved = false
                }
            }
            // A failed batch must not erase history or suppress successful, independent batches.
            for (batch in (localIds - fetchedIds).sorted().chunked(100)) {
                try {
                    val loaded = try {
                        gqlQueryLocal(networkLibrary, gqlHeaders, batch, graphQLRepository)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (!hasHelixToken) throw e
                        helixLocal(networkLibrary, helixHeaders, batch, helixRepository)
                    }
                    streams.addAll(loaded.filter { it.channelId in batch })
                    fetchedIds.addAll(batch)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    retry = true
                }
            }
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) return@withLock true
            // An opt-out or local unfollow during a suspended network call must take effect now.
            val currentLocalIds = notificationUsersRepository.loadUsers().mapTo(hashSetOf()) { it.channelId }
            val allowedIds = followed + localIds.intersect(currentLocalIds)
            val highWater = shownNotificationsDao.getAll().associate { it.channelId to it.startedAt }.toMutableMap()
            // An entry without a channel or parsable start is skipped like an offline channel,
            // so one incomplete entry cannot fail its batch or hold the setup baseline open.
            val candidates = streams.mapNotNull { stream ->
                startedAt(stream)?.let { stream to it }
            }.sortedByDescending { it.second }.distinctBy { it.first.channelId }
            for ((stream, startedAt) in candidates) {
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) return@withLock true
                val id = stream.channelId ?: continue
                if (id !in allowedIds) continue
                // Whole seconds: backends may report the same broadcast with different fractions.
                val previous = highWater[id]
                if (previous != null && startedAt / 1000 <= previous / 1000) continue
                // Do not suspend between posting and persisting its acknowledgement. This avoids
                // losing successful deliveries to cooperative cancellation. setOnlyAlertOnce and
                // stable notification tags also make a retry after process death idempotent while
                // the prior notification is still in the tray.
                if ((id !in followed || !state.accountChannelAllowed(accountId, id)) &&
                    notificationUsersRepository.loadUsers().none { it.channelId == id }
                ) continue
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) return@withLock true
                // A broadcast that started after setup began is new even if the baseline is unfinished.
                val baseline = pendingBaseline != null && id !in pendingBaseline.completed &&
                    startedAt / 1000 <= pendingBaseline.startedAt / 1000
                if (baseline || post(stream)) {
                    shownNotificationsDao.insertList(listOf(ShownNotification(id, startedAt)))
                    highWater[id] = startedAt
                    if (baseline) onBaseline(stream)
                } else {
                    retry = true
                }
            }
            if (pendingBaseline != null) {
                val completed = pendingBaseline.completed + fetchedIds
                if (!state.advanceBaseline(accountId, revision, completed, followsResolved && completed.containsAll(localIds))) return@withLock true
            }
            retry
        }
    }

    private suspend fun loadFollowedStreams(networkLibrary: String?, headers: Map<String, String>, graphQLRepository: GraphQLRepository): List<FollowedLiveStream> {
        val list = mutableListOf<FollowedLiveStream>()
        val cursors = hashSetOf<String>()
        var offset: String? = null
        do {
            val response = graphQLRepository.loadQueryUserFollowedStreams(networkLibrary, headers, 100, offset)
            val data = response.data?.user?.followedLiveUsers ?: throw IOException("Missing followed live channels")
            val items = data.edges ?: throw IOException("Missing followed live channel edges")
            items.mapNotNullTo(list) { item ->
                item?.node?.let {
                    FollowedLiveStream(
                        Stream(
                            id = it.stream?.id,
                            channelId = it.id,
                            channelLogin = it.login,
                            channelName = it.displayName,
                            channelImageURL = it.profileImageURL,
                            gameId = it.stream?.game?.id,
                            gameSlug = it.stream?.game?.slug,
                            gameName = it.stream?.game?.displayName,
                            title = it.stream?.broadcaster?.broadcastSettings?.title,
                            thumbnailURL = it.stream?.previewImageURL,
                            createdAt = it.stream?.createdAt?.toString(),
                            viewerCount = it.stream?.viewersCount,
                            tags = it.stream?.freeformTags?.mapNotNull { tag -> tag.name },
                        ),
                        it.self?.follower?.notificationSettings?.isEnabled,
                    )
                }
            }
            offset = items.lastOrNull()?.cursor?.toString()?.takeIf { data.pageInfo?.hasNextPage == true && it.isNotBlank() }
            if (offset != null && !cursors.add(offset)) throw IOException("Repeated followed live pagination cursor")
        } while (offset != null)
        return list
    }

    private suspend fun loadHelixFollowedStreams(networkLibrary: String?, headers: Map<String, String>, accountId: String, helixRepository: HelixRepository): List<Stream> {
        val list = mutableListOf<Stream>()
        val cursors = hashSetOf<String>()
        var offset: String? = null
        do {
            val response = helixRepository.getFollowedStreams(networkLibrary, headers, accountId, 100, offset)
            response.data.mapTo(list) { it.toStream() }
            offset = response.pagination?.cursor?.takeIf { it.isNotBlank() }
            if (offset != null && !cursors.add(offset)) throw IOException("Repeated followed live pagination cursor")
        } while (offset != null)
        return list
    }

    private suspend fun gqlQueryLocal(networkLibrary: String?, gqlHeaders: Map<String, String>, ids: List<String>, graphQLRepository: GraphQLRepository): List<Stream> {
        val response = graphQLRepository.loadQueryUsersStream(networkLibrary, gqlHeaders, ids)
        val items = response.data?.users ?: throw IOException("Missing live stream response")
        return items.mapNotNull { item ->
            item?.stream?.let { stream ->
                Stream(
                    id = stream.id,
                    channelId = item.id,
                    channelLogin = item.login,
                    channelName = item.displayName,
                    channelImageURL = item.profileImageURL,
                    gameId = stream.game?.id,
                    gameSlug = stream.game?.slug,
                    gameName = stream.game?.displayName,
                    title = stream.broadcaster?.broadcastSettings?.title,
                    thumbnailURL = stream.previewImageURL,
                    createdAt = stream.createdAt?.toString(),
                    viewerCount = stream.viewersCount,
                    tags = stream.freeformTags?.mapNotNull { it.name },
                )
            }
        }
    }

    private suspend fun helixLocal(networkLibrary: String?, helixHeaders: Map<String, String>, ids: List<String>, helixRepository: HelixRepository): List<Stream> =
        helixRepository.getStreams(networkLibrary = networkLibrary, headers = helixHeaders, ids = ids, limit = 100).data.map { it.toStream() }

    // Profile artwork is optional; an unrelated /users failure must not lose alerts.
    private fun com.github.andreyasadchy.xtra.model.helix.stream.Stream.toStream() =
        Stream(id = id, channelId = channelId, channelLogin = channelLogin,
            channelName = channelName, gameId = gameId, gameName = gameName,
            title = title, thumbnailURL = thumbnailURL, createdAt = startedAt,
            viewerCount = viewerCount, tags = tags)

    suspend fun accountNotificationChanged(originatingAccountId: String?, channelId: String, enabled: Boolean) {
        // Invalidate suspended polls immediately; durable cache writes stay off the UI thread.
        val revision = state.invalidateAccount(originatingAccountId, channelId, enabled) ?: return
        withContext(Dispatchers.IO) { state.persistAccountNotificationChanged(originatingAccountId, channelId, enabled, revision) }
    }

    suspend fun saveList(list: List<ShownNotification>) = withContext(Dispatchers.IO) {
        pollMutex.withLock {
            // Acknowledgements only move forward, so a stale baseline cannot re-arm an older alert.
            val highWater = shownNotificationsDao.getAll().associate { it.channelId to it.startedAt }
            list.filter { it.channelId.isNotBlank() && it.startedAt > (highWater[it.channelId] ?: 0L) }
                .sortedByDescending { it.startedAt }.distinctBy { it.channelId }
                .takeIf { it.isNotEmpty() }?.let { shownNotificationsDao.insertList(it) }
        }
    }

    private fun startedAt(stream: Stream): Long? =
        if (stream.channelId.isNullOrBlank()) null else liveNotificationStartedAt(stream.createdAt)

    private class FollowedLiveStream(val stream: Stream, val notificationsEnabled: Boolean?)

    companion object {
        // Baseline, manual acknowledgements and workers share one delivery lane in this process.
        private val pollMutex = Mutex()
    }
}
