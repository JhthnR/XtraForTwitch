package com.github.andreyasadchy.xtra.ui.chat

import android.app.Application
import android.os.Looper
import com.apollographql.apollo.api.ApolloResponse
import com.github.andreyasadchy.xtra.graphql.VideoCommentsQuery
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.LooperMode
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatReplayResumeTest {
    private val repository = mock<GraphQLRepository>()
    private val model = ChatViewModel(RuntimeEnvironment.getApplication(), mock(),
        repository, mock(), mock(), null, Json)
    private var position: Long? = 90_000
    private fun createReplay() = model.startReplayChat("fixture-vod", 5, null,
        { position }, { 1.5f }, "123", "example")
    private fun resume() = model.resumeReplay("123", "example", null, "fixture-vod", 5,
        { position }, { 1.5f })

    private fun response(offset: Int) = ApolloResponse.Builder(VideoCommentsQuery(), UUID.randomUUID())
        .data(VideoCommentsQuery.Data(VideoCommentsQuery.Video(VideoCommentsQuery.Comments(
            listOf(VideoCommentsQuery.Edge(null, VideoCommentsQuery.Node(
                VideoCommentsQuery.Commenter("Fixture", "123", "fixture"), offset,
                "message-$offset", VideoCommentsQuery.Message(
                    listOf(VideoCommentsQuery.Fragment(null, "at-$offset")), emptyList(), null)))),
            VideoCommentsQuery.PageInfo(false))))).build()

    private fun stubComments() = runBlocking {
        whenever(repository.loadQueryVideoComments(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenAnswer { response(it.getArgument<Int?>(3) ?: 0) }
    }

    private fun awaitMessage(message: String) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            if (model.chatMessages.any { it.message == message }) return
            Thread.sleep(10)
        }
        fail("Replay did not deliver $message without another player-ready event")
    }

    @Test fun `resume of an already ready player delivers comments at its current position once`() {
        stubComments()
        try {
            createReplay()
            model.startReplayChatLoad()
            awaitMessage("at-95")
            model.stopReplayChat()
            clearInvocations(repository)
            position = 120_000
            resume() // No READY event: playback continued in PiP/background.
            awaitMessage("at-125")
            resume()
            model.startReplayChatLoad() // A later READY must remain idempotent.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            runBlocking {
                verify(repository, times(1)).loadQueryVideoComments(anyOrNull(), any(), eq("fixture-vod"), eq(125), isNull())
                verifyNoMoreInteractions(repository)
            }
            assertEquals(1, model.chatMessages.count { it.message == "at-125" })
        } finally { model.stopReplayChat() }
    }

    @Test fun `resume waits for player position when reconnecting then starts once on ready`() {
        stubComments()
        try {
            createReplay()
            model.stopReplayChat()
            position = null
            resume()
            shadowOf(Looper.getMainLooper()).idle()
            verifyNoInteractions(repository)
            position = 240_000
            model.startReplayChatLoad()
            awaitMessage("at-245")
            model.startReplayChatLoad()
            runBlocking {
                verify(repository, times(1)).loadQueryVideoComments(anyOrNull(), any(), eq("fixture-vod"), eq(245), isNull())
            }
        } finally { model.stopReplayChat() }
    }

    @Test fun `local replay can be armed before its asynchronous file messages arrive`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val received = mutableListOf<ChatMessage>()
        val local = ChatReplayManagerLocal({ 120_000 }, { 1.5f }, scope,
            object : ChatReplayManager.Listener {
                override suspend fun onChatMessage(message: ChatMessage) { received += message }
            })
        try {
            local.startLoad()
            local.setMessages(listOf(ChatMessage(id = "local", message = "downloaded", timestamp = 120_000)), 0)
            repeat(200) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
                if (received.isNotEmpty()) return@repeat
                Thread.sleep(10)
            }
            assertEquals(listOf("downloaded"), received.map { it.message })
            local.startLoad()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            assertEquals(1, received.size)
        } finally { local.stop(); scope.cancel() }
    }
}
