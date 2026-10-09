package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointRewardRedemptionResponse.Outcome
import com.github.andreyasadchy.xtra.model.ui.ChannelPointRewardOption
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ChannelPointRewardsRepositoryTest {
    private val reward = ChannelPointRewardOption("reward", "A \"quoted\" title", 100, "Prompt", true, true)
    private val json = Json { ignoreUnknownKeys = true }
    private fun repository(client: OkHttpClient = OkHttpClient()) = ChannelPointRewardsRepository(mock(), client, json)

    @Test fun `custom reward payload preserves confirmed cost title input and transaction`() {
        val body = json.parseToJsonElement(repository().redemptionBody("channel", reward, "hello\nworld", "transaction")).jsonObject
        assertEquals("RedeemCommunityPointsCustomReward", body.getValue("operationName").jsonPrimitive.content)
        val input = body.getValue("variables").jsonObject.getValue("input").jsonObject
        assertEquals("channel", input.getValue("channelID").jsonPrimitive.content)
        assertEquals("100", input.getValue("cost").jsonPrimitive.content)
        assertEquals(reward.title, input.getValue("title").jsonPrimitive.content)
        assertEquals("hello\nworld", input.getValue("textInput").jsonPrimitive.content)
        assertEquals("transaction", input.getValue("transactionID").jsonPrimitive.content)
    }
    @Test fun `non-input reward does not transmit stale typed text`() {
        val body = repository().redemptionBody("channel", reward.copy(requiresInput = false), "private draft", "transaction")
        assertFalse(body.contains("private draft"))
    }
    @Test fun `mutation is one shot and uses captured authenticated account`() = runTest {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            assertEquals("OAuth fixture", chain.request().header(C.HEADER_TOKEN))
            assertTrue(chain.request().body!!.isOneShot())
            val body = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            assertTrue(body.contains("\"transactionID\":\"fixed-id\""))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":{"redeemCommunityPointsCustomReward":{"error":null}}}""".toResponseBody()).build()
        }.build()
        val result = repository(client).redeem(mapOf(C.HEADER_TOKEN to "OAuth fixture"), "channel", reward, "hello", "fixed-id")
        assertEquals(Outcome.SUCCESS, result.outcome())
        assertEquals(1, requests.get())
    }
    @Test fun `transport failure is not retried`() = runTest {
        val count = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { count.incrementAndGet(); throw IOException("fixture failure") }.build()
        try {
            repository(client).redeem(mapOf(C.HEADER_TOKEN to "fixture"), "channel", reward, "hello", "transaction")
            fail("Expected an uncertain failure")
        } catch (_: IOException) { }
        assertEquals(1, count.get())
    }
    @Test fun `mutation client disables OkHttp connection retries and redirects`() {
        // OkHttp replays below application interceptors, so assert the client configuration itself.
        val inherited = OkHttpClient.Builder().retryOnConnectionFailure(true).followRedirects(true).followSslRedirects(true).build()
        val client = repository(inherited).mutationClient
        assertFalse(client.retryOnConnectionFailure)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }
    @Test fun `gateway refusal before execution is a definite rejection`() = runTest {
        val count = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            count.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
                .body("""{"error":"Unauthorized"}""".toResponseBody()).build()
        }.build()
        val result = repository(client).redeem(mapOf(C.HEADER_TOKEN to "fixture"), "channel", reward, "hello", "transaction")
        assertEquals(Outcome.REJECTED, result.outcome())
        assertEquals(1, count.get())
    }
    @Test fun `server error after dispatch stays uncertain`() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(502).message("Bad Gateway")
                .body("".toResponseBody()).build()
        }.build()
        try {
            repository(client).redeem(mapOf(C.HEADER_TOKEN to "fixture"), "channel", reward, "hello", "transaction")
            fail("Expected an uncertain failure")
        } catch (_: IOException) { }
    }
    @Test fun `missing token is rejected before network`() = runTest {
        val count = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { count.incrementAndGet(); throw IOException() }.build()
        try { repository(client).redeem(emptyMap(), "channel", reward, "hello", "transaction"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, count.get())
    }
    @Test fun `inherited debug body logger cannot read authorization or reward input`() = runTest {
        val logs = mutableListOf<String>()
        val logger = HttpLoggingInterceptor { logs.add(it) }.apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(logger).addNetworkInterceptor(logger).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":{"redeemCommunityPointsCustomReward":{"error":null}}}""".toResponseBody()).build()
        }.build()
        repository(client).redeem(mapOf(C.HEADER_TOKEN to "private-token"), "channel", reward, "private message", "transaction")
        assertTrue(logs.isEmpty())
        assertTrue(client.withoutRewardLogging().interceptors.none { it is HttpLoggingInterceptor })
        assertTrue(client.withoutRewardLogging().networkInterceptors.none { it is HttpLoggingInterceptor })
    }
}
