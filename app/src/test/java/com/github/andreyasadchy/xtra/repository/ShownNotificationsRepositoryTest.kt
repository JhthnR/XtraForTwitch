package com.github.andreyasadchy.xtra.repository

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.parseResponse
import com.github.andreyasadchy.xtra.db.ShownNotificationsDao
import com.github.andreyasadchy.xtra.graphql.UsersStreamQuery
import com.github.andreyasadchy.xtra.graphql.UserFollowedStreamsQuery
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.helix.Pagination
import com.github.andreyasadchy.xtra.model.helix.stream.StreamsResponse
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.util.C
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.buffer
import okio.source
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import com.github.andreyasadchy.xtra.model.helix.stream.Stream as HelixStream

class ShownNotificationsRepositoryTest {
    private val dao = MemoryDao()
    private val state: LiveNotificationState = mock()
    private val local: NotificationUsersRepository = mock()
    private val gql: GraphQLRepository = mock()
    private val helix: HelixRepository = mock()
    private val repository = ShownNotificationsRepository(dao, state)
    private val cache = mutableMapOf<String, Set<String>>()
    private var account: String? = null
    private var current = true
    private var revision = 0L
    private var pendingBaseline: Set<String>? = null
    private var localIds = listOf("1")
    private var follows = listOf("1")
    private var streams = listOf(live("1"))
    private var gqlHeaders = emptyMap<String, String>()
    private val helixHeaders = mapOf(C.HEADER_TOKEN to "fixture-helix")
    private val posted = mutableListOf<Stream>()

    @Before fun setup(): Unit = runBlocking {
        whenever(state.accountId()).thenAnswer { account }
        whenever(state.accountChannelAllowed(anyOrNull(), any())).thenReturn(true)
        whenever(state.revision()).thenAnswer { revision }
        whenever(state.baselineCompleted(anyOrNull())).thenAnswer { pendingBaseline }
        doAnswer { pendingBaseline = emptySet(); revision++; null }.whenever(state).beginBaseline(anyOrNull())
        whenever(state.advanceBaseline(anyOrNull(), any(), any(), any())).thenAnswer {
            pendingBaseline = if (it.getArgument<Boolean>(3)) null else it.getArgument<Set<String>>(2)
            true
        }
        whenever(state.isCurrent(anyOrNull(), any(), any())).thenAnswer { current && it.getArgument<String?>(0) == account }
        whenever(state.load(any())).thenAnswer { cache[it.getArgument<String>(0)] }
        whenever(state.saveIfCurrent(any(), any(), any())).thenAnswer {
            cache[it.getArgument(0)] = it.getArgument(2)
            true
        }
        whenever(local.loadUsers()).thenAnswer { localIds.map(::NotificationUser) }
        whenever(gql.loadQueryUsersStream(anyOrNull(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(ApolloResponse.Builder(UsersStreamQuery(), UUID.randomUUID()).data(UsersStreamQuery.Data(null)).build())
        whenever(gql.loadQueryUserFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(ApolloResponse.Builder(UserFollowedStreamsQuery(), UUID.randomUUID()).data(UserFollowedStreamsQuery.Data(null)).build())
        whenever(helix.getFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenAnswer { StreamsResponse(streams.filter { it.channelId in follows }, Pagination()) }
        doAnswer { call -> StreamsResponse(streams.filter { it.channelId in call.getArgument<List<String>>(2) }) }
            .whenever(helix).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    private suspend fun poll(post: (Stream) -> Boolean = { posted.add(it); true }): Boolean =
        repository.notifyNewStreams(local, "OkHttp", gqlHeaders, gql, helixHeaders, helix, post)

    @Test fun `successful delivery is acknowledged and repeated polls are silent`() = runTest {
        assertFalse(poll())
        assertFalse(poll())
        assertEquals(listOf("1"), posted.map { it.channelId })
        assertEquals(1, dao.items.size)
    }

    @Test fun `duplicate responses and local account overlap produce one newest broadcast`() = runTest {
        account = "viewer"
        streams = listOf(live("1"), live("1", NEWER), live("1"))
        poll()
        assertEquals(listOf(NEWER), posted.map { it.createdAt })
    }

    @Test fun `offline then same broadcast stays suppressed but next broadcast alerts`() = runTest {
        poll()
        streams = emptyList()
        poll()
        streams = listOf(live("1"))
        poll()
        streams = listOf(live("1", NEWER))
        poll()
        assertEquals(listOf(START, NEWER), posted.map { it.createdAt })
        assertEquals(0, dao.deletes)
    }

    @Test fun `older replay and malformed timestamp cannot regress acknowledged high water`() = runTest {
        streams = listOf(live("1", NEWER))
        poll()
        streams = listOf(live("1"), live("1", "broken"))
        poll()
        streams = listOf(live("1", NEWER))
        poll()
        assertEquals(1, posted.size)
        assertEquals(liveNotificationStartedAt(NEWER), dao.items["1"])
    }

    @Test fun `failed posting is not acknowledged and a subsequent poll retries it`() = runTest {
        assertTrue(poll { false })
        assertTrue(dao.items.isEmpty())
        assertFalse(poll())
        assertEquals(1, posted.size)
    }

    @Test fun `posting exception retains prior deliveries and retries remaining stream`() = runTest {
        localIds = listOf("1", "2")
        streams = listOf(live("1"), live("2"))
        try {
            poll { if (it.channelId == "2") throw SecurityException("posting blocked") else { posted.add(it); true } }
            fail("Expected posting failure")
        } catch (_: SecurityException) { }
        assertEquals(setOf("1"), dao.items.keys)
        poll()
        assertEquals(listOf("1", "2"), posted.map { it.channelId })
    }

    @Test fun `complete fetch failure never removes acknowledged history`() = runTest {
        poll()
        doAnswer { throw IOException("offline") }.whenever(helix).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertTrue(poll())
        assertEquals(1, dao.items.size)
        assertEquals(1, posted.size)
        assertEquals(0, dao.deletes)
    }

    @Test fun `a failed stream batch still delivers another successful batch`() = runTest {
        localIds = (1..101).map { it.toString().padStart(3, '0') }
        doAnswer { call ->
            val ids = call.getArgument<List<String>>(2)
            if (ids.size == 100) throw IOException("first batch failed")
            assertEquals(100, call.getArgument<Int>(6))
            StreamsResponse(listOf(live(ids.single())))
        }.whenever(helix).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertTrue(poll())
        assertEquals(listOf("101"), posted.map { it.channelId })
    }

    @Test fun `Helix-only account alerts each live follow from one followed-streams request`() = runTest {
        account = "viewer"
        localIds = emptyList()
        follows = listOf("1", "2", "3")
        poll()
        assertEquals(listOf("1"), posted.map { it.channelId })
        streams = listOf(live("2"))
        poll()
        assertEquals(listOf("1", "2"), posted.map { it.channelId })
        verify(helix, times(2)).getFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull())
        verify(helix, never()).getUserFollows(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        verify(state, never()).saveIfCurrent(any(), any(), any())
    }

    @Test fun `follow outage retries without alerting or changing cached preferences`() = runTest {
        account = "viewer"
        localIds = emptyList()
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        cache["viewer"] = setOf("1")
        doAnswer { throw IOException("follow API unavailable") }.whenever(helix).getFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull())
        assertTrue(poll())
        assertTrue(posted.isEmpty())
        assertEquals(setOf("1"), cache["viewer"])
    }

    @Test fun `reported live preferences update the cache and keep offline channels`() = runTest {
        account = "viewer"
        localIds = emptyList()
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        cache["viewer"] = setOf("2", "3")
        whenever(gql.loadQueryUserFollowedStreams("OkHttp", gqlHeaders, 100, null))
            .thenReturn(followPage(listOf("1" to true, "2" to false), null, false))
        assertFalse(poll())
        assertEquals(setOf("1", "3"), cache["viewer"])
        assertEquals(listOf("1"), posted.map { it.channelId })
        verify(local, never()).deleteUser(any())
    }

    @Test fun `local opt in still alerts a live follow muted on Twitch`() = runTest {
        account = "viewer"
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        whenever(gql.loadQueryUserFollowedStreams("OkHttp", gqlHeaders, 100, null))
            .thenReturn(followPage(listOf("1" to false), null, false))
        assertFalse(poll())
        assertEquals(listOf("1"), posted.map { it.channelId })
        verify(gql, never()).loadQueryUsersStream(anyOrNull(), any(), anyOrNull(), anyOrNull())
    }

    @Test fun `failed GQL preferences with successful Helix follows retain mute and unfollow choices`() = runTest {
        account = "viewer"
        localIds = emptyList()
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        cache["viewer"] = setOf("2", "4")
        follows = listOf("1", "2", "3")
        streams = follows.map { live(it) }
        assertTrue(poll())
        assertEquals(setOf("2", "4"), cache["viewer"])
        assertEquals(listOf("2"), posted.map { it.channelId })
    }

    @Test fun `unknown failed GQL preferences do not opt in every account follow`() = runTest {
        account = "viewer"
        localIds = emptyList()
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        assertTrue(poll())
        assertTrue(posted.isEmpty())
        assertNull(cache["viewer"])
    }

    @Test fun `account switch cannot reuse another accounts cached follows`() = runTest {
        cache["old"] = setOf("1")
        account = "new"
        localIds = emptyList()
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        assertTrue(poll())
        assertTrue(posted.isEmpty())
        verify(state).load("new")
    }

    @Test fun `sign out ignores retained account cache and still respects local opt ins`() = runTest {
        cache["viewer"] = setOf("2")
        streams = listOf(live("1"), live("2"))
        poll()
        assertEquals(listOf("1"), posted.map { it.channelId })
        verify(state, never()).load(any())
    }

    @Test fun `account or credential change during network fetch prevents stale delivery`() = runTest {
        doAnswer { current = false; StreamsResponse(streams) }
            .whenever(helix).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertTrue(poll())
        assertTrue(posted.isEmpty())
        assertTrue(dao.items.isEmpty())
    }

    @Test fun `local opt out during network fetch prevents pending alert`() = runTest {
        doAnswer { localIds = emptyList(); StreamsResponse(streams) }
            .whenever(helix).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        poll()
        assertTrue(posted.isEmpty())
        assertTrue(dao.items.isEmpty())
    }

    @Test fun `cancellation propagates without Helix fallback or acknowledgement`() = runTest {
        whenever(gql.loadQueryUsersStream(anyOrNull(), any(), anyOrNull(), anyOrNull())).thenThrow(CancellationException("stopped"))
        try { poll(); fail("Expected cancellation") } catch (_: CancellationException) { }
        verify(helix, never()).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertTrue(dao.items.isEmpty())
    }

    @Test fun `setup baseline acknowledges live broadcasts without alerting them later`() = runTest {
        val baseline = repository.getNewStreams(local, "OkHttp", gqlHeaders, gql, helixHeaders, helix)
        assertEquals(listOf("1"), baseline.map { it.channelId })
        poll()
        assertTrue(posted.isEmpty())
        streams = listOf(live("1", NEWER))
        poll()
        assertEquals(listOf(NEWER), posted.map { it.createdAt })
    }

    @Test fun `manual acknowledgement remains monotonic across duplicate and older baselines`() = runTest {
        repository.saveList(listOf(ShownNotification("1", 20), ShownNotification("1", 10)))
        repository.saveList(listOf(ShownNotification("1", 1), ShownNotification("", 30), ShownNotification("2", -1)))
        assertEquals(mapOf("1" to 20L), dao.items)
    }

    @Test fun `concurrent workers cannot notify the same broadcast twice`() = runTest {
        listOf(async { poll() }, async { poll() }, async { poll() }).awaitAll()
        assertEquals(1, posted.size)
    }

    @Test fun `GraphQL live follows paginate before persistence and respect every preference`() = runTest {
        account = "viewer"
        localIds = emptyList()
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        whenever(gql.loadQueryUserFollowedStreams("OkHttp", gqlHeaders, 100, null))
            .thenReturn(followPage(listOf("1" to false), "next", true))
        whenever(gql.loadQueryUserFollowedStreams("OkHttp", gqlHeaders, 100, "next"))
            .thenReturn(followPage(listOf("2" to true), null, false))
        assertFalse(poll())
        assertEquals(setOf("2"), cache["viewer"])
        assertEquals(listOf("2"), posted.map { it.channelId })
        verify(helix, never()).getFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `repeated cursor or partial GQL pages never replace last complete preference cache`() = runTest {
        account = "viewer"
        localIds = emptyList()
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        val saved = setOf("2")
        cache["viewer"] = saved
        whenever(gql.loadQueryUserFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(followPage(listOf("1" to true), "same", true))
        doAnswer { throw IOException("offline") }.whenever(helix).getFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull())
        assertTrue(poll())
        assertEquals(saved, cache["viewer"])
        verify(state, never()).saveIfCurrent(any(), any(), any())
    }

    @Test fun `Helix live follows paginate`() = runTest {
        account = "viewer"
        localIds = emptyList()
        whenever(helix.getFollowedStreams("OkHttp", helixHeaders, "viewer", 100, null))
            .thenReturn(StreamsResponse(listOf(live("1")), Pagination("next")))
        whenever(helix.getFollowedStreams("OkHttp", helixHeaders, "viewer", 100, "next"))
            .thenReturn(StreamsResponse(listOf(live("2")), Pagination()))
        assertFalse(poll())
        assertEquals(listOf("1", "2"), posted.mapNotNull { it.channelId }.sorted())
    }

    @Test fun `GraphQL live lookup works without a Helix fallback or a viewer count`() = runTest {
        val response = """{"data":{"users":[{"id":"1","displayName":"One","login":"one","profileImageURL":null,"stream":{"id":"broadcast","createdAt":"$START","viewersCount":null,"game":null,"broadcaster":null,"previewImageURL":null,"freeformTags":null}}]}}"""
            .byteInputStream().source().buffer().jsonReader().use { UsersStreamQuery().parseResponse(it) }
        whenever(gql.loadQueryUsersStream("OkHttp", gqlHeaders, listOf("1"))).thenReturn(response)
        assertFalse(poll())
        assertEquals(listOf("1"), posted.map { it.channelId })
        verify(helix, never()).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `follow synchronization cancellation never falls back or mutates cache`() = runTest {
        account = "viewer"
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        whenever(gql.loadQueryUserFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull()))
            .thenThrow(CancellationException("stopped"))
        try { poll(); fail("Expected cancellation") } catch (_: CancellationException) { }
        verify(state, never()).saveIfCurrent(any(), any(), any())
        verify(helix, never()).getFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull())
        assertTrue(dao.items.isEmpty())
    }

    @Test fun `account changed during follow refresh cannot overwrite current cache`() = runTest {
        account = "old"
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        doAnswer { account = "new"; followPage(listOf("1" to true), null, false) }
            .whenever(gql).loadQueryUserFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull())
        assertTrue(poll())
        verify(state, never()).saveIfCurrent(any(), any(), any())
        assertTrue(posted.isEmpty())
    }

    @Test fun `unrelated server stream IDs cannot create notifications`() = runTest {
        doAnswer { StreamsResponse(listOf(live("not-requested"))) }
            .whenever(helix).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        poll()
        assertTrue(posted.isEmpty())
        assertTrue(dao.items.isEmpty())
    }

    @Test fun `failed baseline retries quietly after recovery then alerts subsequent broadcasts`() = runTest {
        doAnswer { throw IOException("offline") }.whenever(helix).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        repository.getNewStreams(local, "OkHttp", gqlHeaders, gql, helixHeaders, helix)
        assertNotNull(pendingBaseline)
        doAnswer { StreamsResponse(streams) }.whenever(helix).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        poll()
        assertNull(pendingBaseline)
        assertTrue(posted.isEmpty())
        streams = listOf(live("1", NEWER))
        poll()
        assertEquals(listOf(NEWER), posted.map { it.createdAt })
    }

    @Test fun `unresolved account baseline does not permanently silence initialized local channels`() = runTest {
        account = "viewer"
        gqlHeaders = mapOf(C.HEADER_TOKEN to "fixture-gql")
        doAnswer { throw IOException("offline") }.whenever(helix).getFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull())
        repository.getNewStreams(local, "OkHttp", gqlHeaders, gql, helixHeaders, helix)
        assertEquals(setOf("1"), pendingBaseline)
        streams = listOf(live("1", NEWER), live("2"))
        poll()
        assertEquals(listOf("1"), posted.map { it.channelId })
        whenever(gql.loadQueryUserFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(followPage(listOf("2" to true), null, false))
        poll()
        assertNull(pendingBaseline)
        assertEquals(listOf("1"), posted.map { it.channelId })
    }

    @Test fun `local opt out between deliveries prevents second local alert`() = runTest {
        localIds = listOf("1", "2")
        streams = listOf(live("1"), live("2"))
        poll { posted.add(it); localIds = listOf("1"); true }
        assertEquals(listOf("1"), posted.map { it.channelId })
    }

    @Test fun `remote mute generation during fetch prevents stale account delivery`() = runTest {
        account = "viewer"
        localIds = emptyList()
        doAnswer { revision++; StreamsResponse(streams, Pagination()) }
            .whenever(helix).getFollowedStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull())
        assertTrue(poll())
        assertTrue(posted.isEmpty())
        assertTrue(dao.items.isEmpty())
    }

    @Test fun `same broadcast with cross backend fractional timestamps alerts once`() = runTest {
        streams = listOf(HelixStream(id = "same", channelId = "1", startedAt = START))
        poll()
        streams = listOf(HelixStream(id = "same", channelId = "1", startedAt = "2026-10-04T10:00:00.123456Z"))
        poll()
        assertEquals(1, posted.size)
    }

    @Test fun `legacy acknowledgement suppresses the same broadcast`() = runTest {
        repository.saveList(listOf(ShownNotification("1", liveNotificationStartedAt(START)!!)))
        streams = listOf(HelixStream(id = "legacy", channelId = "1", startedAt = START))
        poll()
        streams = listOf(HelixStream(id = "legacy", channelId = "1", startedAt = "2026-10-04T10:00:00.123Z"))
        poll()
        assertTrue(posted.isEmpty())
    }

    @Test fun `manual enable of a later broadcast stays quiet`() = runTest {
        poll()
        repository.saveList(listOf(ShownNotification("1", liveNotificationStartedAt(NEWER)!!)))
        streams = listOf(HelixStream(id = "later", channelId = "1", startedAt = NEWER))
        poll()
        assertEquals(1, posted.size)
    }

    @Test fun `manual fractional baseline does not suppress a genuine quick restart`() = runTest {
        repository.saveList(listOf(ShownNotification("1", liveNotificationStartedAt("2026-10-04T10:00:00.123456Z")!!)))
        streams = listOf(HelixStream(id = "restart", channelId = "1", startedAt = "2026-10-04T10:00:01Z"))
        poll()
        assertEquals(listOf("restart"), posted.map { it.id })
    }

    @Test fun `incomplete live entry is skipped without failing its batch or the baseline`() = runTest {
        localIds = listOf("1", "2")
        streams = listOf(HelixStream(id = "live", channelId = "1", startedAt = null), live("2"))
        repository.getNewStreams(local, "OkHttp", gqlHeaders, gql, helixHeaders, helix)
        assertNull(pendingBaseline)
        streams = listOf(HelixStream(id = "live", channelId = "1", startedAt = null), live("2", NEWER))
        assertFalse(poll())
        assertEquals(listOf("2"), posted.map { it.channelId })
    }

    @Test fun `GraphQL partial errors keep the valid entries of a batch`() = runTest {
        localIds = listOf("1", "2")
        val response = """{"errors":[{"message":"user unavailable"}],"data":{"users":[null,{"id":"2","displayName":"Two","login":"two","profileImageURL":null,"stream":{"id":"broadcast","createdAt":"$START","viewersCount":1,"game":null,"broadcaster":null,"previewImageURL":null,"freeformTags":null}}]}}"""
            .byteInputStream().source().buffer().jsonReader().use { UsersStreamQuery().parseResponse(it) }
        whenever(gql.loadQueryUsersStream("OkHttp", gqlHeaders, listOf("1", "2"))).thenReturn(response)
        assertFalse(poll())
        assertEquals(listOf("2"), posted.map { it.channelId })
        verify(helix, never()).getStreams(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `pending remote opt out blocks polls started before durable cache update`() = runTest {
        account = "viewer"
        localIds = emptyList()
        whenever(state.accountChannelAllowed("viewer", "1")).thenReturn(false)
        poll()
        assertTrue(posted.isEmpty())
        assertTrue(dao.items.isEmpty())
    }

    private fun followPage(items: List<Pair<String, Boolean>>, cursor: String?, more: Boolean): ApolloResponse<UserFollowedStreamsQuery.Data> {
        val edges = items.joinToString(",") { (id, enabled) ->
            """{"cursor":${cursor?.let { "\"$it\"" } ?: "null"},"node":{"displayName":"User$id","id":"$id","login":"user$id","profileImageURL":null,"self":{"follower":{"notificationSettings":{"isEnabled":$enabled}}},"stream":{"broadcaster":null,"createdAt":"$START","game":null,"id":"stream-$id","previewImageURL":null,"freeformTags":null,"viewersCount":0}}}"""
        }
        return """{"data":{"user":{"followedLiveUsers":{"edges":[$edges],"pageInfo":{"hasNextPage":$more}}}}}"""
            .byteInputStream().source().buffer().jsonReader().use { UserFollowedStreamsQuery().parseResponse(it) }
    }

    private class MemoryDao : ShownNotificationsDao {
        val items = linkedMapOf<String, Long>()
        var deletes = 0
        override fun getAll() = items.map { ShownNotification(it.key, it.value) }
        override fun insertList(items: List<ShownNotification>) { items.forEach { this.items[it.channelId] = it.startedAt } }
        override fun deleteList(items: List<ShownNotification>) { deletes++; items.forEach { this.items.remove(it.channelId) } }
    }

    companion object {
        private const val START = "2026-10-04T10:00:00Z"
        private const val NEWER = "2026-10-04T11:00:00Z"
        private fun live(id: String, start: String = START) = HelixStream(id = "stream-$id-$start", channelId = id, startedAt = start, viewerCount = 0)
    }
}
