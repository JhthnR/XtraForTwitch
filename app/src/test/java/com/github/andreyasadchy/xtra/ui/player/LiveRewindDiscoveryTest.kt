package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.parseResponse
import com.github.andreyasadchy.xtra.graphql.UsersStreamQuery
import com.github.andreyasadchy.xtra.graphql.UserVideosQuery
import com.github.andreyasadchy.xtra.graphql.type.BroadcastType
import com.github.andreyasadchy.xtra.graphql.type.VideoSort
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class LiveRewindDiscoveryTest {
    private val gql: GraphQLRepository = mock()
    private val repository: PlayerRepository = mock()
    private val start = Instant.ofEpochMilli(System.currentTimeMillis() - 600_000L).toString()
    private val expected = Stream(id = "live", channelId = "channel", channelLogin = "login", createdAt = start)
    private var status = 200
    private var playlist = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nhttps://example.test/variant.m3u8"
    private var mediaPlaylist: String? = null
    private var playlistRequests = 0
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        playlistRequests++
        val media = mediaPlaylist?.takeIf { chain.request().url.encodedPath.endsWith("/variant.m3u8") }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(status).message("fixture").body((media ?: playlist).toResponseBody()).build()
    }.build()
    private val vm = PlayerViewModel(gql, mock(), mock(), mock(), mock(), mock(), null, null, mock(), client, repository, mock(), mock())

    private fun stream(id: String? = "live", created: String = start) = UsersStreamQuery().parseResponse(Buffer().writeUtf8("""
        {"data":{"users":[{"__typename":"User","id":"channel","displayName":"Channel","login":"login","profileImageURL":null,
        "stream":${if (id == null) "null" else """{"__typename":"Stream","id":"$id","createdAt":"$created","broadcaster":null,"game":null,"previewImageURL":null,"freeformTags":[],"viewersCount":1}"""}}]}}
    """).jsonReader())

    private fun videos(recording: Boolean = true) = UserVideosQuery().parseResponse(Buffer().writeUtf8("""
        {"data":{"user":{"__typename":"User","displayName":"Channel","login":"login","profileImageURL":null,
        "videos":{"__typename":"VideoConnection","edges":[${if (!recording) "" else """{"__typename":"VideoEdge","cursor":"cursor","node":{"__typename":"Video","id":"123","createdAt":"$start","lengthSeconds":600,"animatedPreviewURL":null,"broadcastType":"ARCHIVE","game":null,"previewThumbnailURL":null,"title":"Broadcast","viewCount":1}}"""}],"pageInfo":{"__typename":"PageInfo","hasNextPage":false}}}}}
    """).jsonReader())

    private suspend fun setup() {
        whenever(gql.loadQueryUsersStream("OkHttp", emptyMap(), listOf("channel"), null)).thenReturn(stream())
        whenever(gql.loadQueryUserVideos("OkHttp", emptyMap(), "channel", null, VideoSort.TIME, listOf(BroadcastType.ARCHIVE), 5, null)).thenReturn(videos())
        whenever(repository.loadVideoPlaylistUrl("OkHttp", emptyMap(), "123", "test", "h264", false))
            .thenReturn("https://example.test/master.m3u8" to emptyList())
    }
    private suspend fun discover() = vm.prepareLiveRewind(expected, 30_000L, "OkHttp", emptyMap(), emptyMap(), "test", "h264", false)

    @Test fun `successful lookup preflights recording and verifies same stream again`() = runTest {
        setup()
        val prepared = discover()
        assertNotNull(prepared)
        assertEquals("123", prepared!!.video.id)
        assertEquals("live", prepared.origin.id)
        // Without a measurable media playlist, GQL lengthSeconds (600s) is the fallback.
        assertEquals(570_000L, prepared.positionMs)
        assertEquals(2, playlistRequests)
        verify(gql, times(2)).loadQueryUsersStream("OkHttp", emptyMap(), listOf("channel"), null)
        assertFalse(vm.started)
        assertNull(vm.videoResult.value)
        assertNull(vm.streamResult.value)
    }

    @Test fun `missing recording leaves current playback state untouched`() = runTest {
        setup()
        whenever(gql.loadQueryUserVideos("OkHttp", emptyMap(), "channel", null, VideoSort.TIME, listOf(BroadcastType.ARCHIVE), 5, null)).thenReturn(videos(false))
        assertNull(discover())
        verifyNoInteractions(repository)
        assertEquals(0, playlistRequests)
    }

    @Test fun `offline or restarted stream cannot use old recording`() = runTest {
        setup()
        whenever(gql.loadQueryUsersStream("OkHttp", emptyMap(), listOf("channel"), null)).thenReturn(stream(null))
        assertNull(discover())
        whenever(gql.loadQueryUsersStream("OkHttp", emptyMap(), listOf("channel"), null)).thenReturn(stream("new"))
        assertNull(discover())
        verifyNoInteractions(repository)
    }

    @Test fun `stream restart during preparation rejects prepared URL`() = runTest {
        setup()
        whenever(gql.loadQueryUsersStream("OkHttp", emptyMap(), listOf("channel"), null)).thenReturn(stream(), stream("new"))
        assertNull(discover())
        assertEquals(2, playlistRequests)
    }

    @Test fun `rewind offset is measured from the recording playlist when GQL length lags`() = runTest {
        setup()
        mediaPlaylist = "#EXTM3U\n#EXT-X-TWITCH-TOTAL-SECS:780.000\n#EXTINF:10.000,\n0.ts"
        assertEquals(750_000L, discover()!!.positionMs)
        mediaPlaylist = "#EXTM3U\n" + (1..72).joinToString("") { "#EXTINF:10.000,\n$it.ts\n" }
        assertEquals(690_000L, discover()!!.positionMs)
    }

    @Test fun `unplayable subscriber recording and non HLS body stay live`() = runTest {
        setup()
        status = 403
        assertNull(discover())
        status = 200
        playlist = "<html>error</html>"
        assertNull(discover())
    }

    @Test fun `cancellation escapes instead of becoming unavailable fallback`() = runTest {
        setup()
        whenever(gql.loadQueryUsersStream("OkHttp", emptyMap(), listOf("channel"), null)).thenThrow(CancellationException("cancelled"))
        try {
            discover()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            verifyNoInteractions(repository)
        }
    }
}
