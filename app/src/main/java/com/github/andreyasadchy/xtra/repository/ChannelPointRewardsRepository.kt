package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointRewardRedemptionResponse
import com.github.andreyasadchy.xtra.model.ui.ChannelPointRewardOption
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ChannelPointRewardsRepository @Inject constructor(
    private val graphQLRepository: GraphQLRepository,
    okHttpClient: OkHttpClient,
    private val json: Json,
) {
    // Spending must not be replayed by a transport retry or redirect. This isolated
    // mutation path deliberately uses OkHttp for every configured network backend.
    private val mutationClient = okHttpClient.withoutRewardLogging().newBuilder()
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()

    suspend fun load(networkLibrary: String?, headers: Map<String, String>, channelLogin: String) =
        graphQLRepository.loadChannelPointRewards(networkLibrary, headers, channelLogin).catalog()

    suspend fun redeem(headers: Map<String, String>, channelId: String, reward: ChannelPointRewardOption, text: String?, transactionId: String): ChannelPointRewardRedemptionResponse {
        require(!headers[C.HEADER_TOKEN].isNullOrBlank())
        require(channelId.isNotBlank() && transactionId.isNotBlank() && reward.available && reward.cost > 0)
        require(!reward.requiresInput || !text.isNullOrBlank())
        val bytes = redemptionBody(channelId, reward, text, transactionId).toByteArray(Charsets.UTF_8)
        val request = Request.Builder().url(TwitchEndpoints.GRAPHQL).headers(headers.toHeaders())
            .post(object : RequestBody() {
                override fun contentType() = "application/json; charset=utf-8".toMediaType()
                override fun contentLength() = bytes.size.toLong()
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
            }).build()
        return suspendCancellableCoroutine { continuation ->
            val call = mutationClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            when {
                                // The gateway answers these before running the mutation, so no points were spent.
                                it.code in REFUSED_BEFORE_RUN -> ChannelPointRewardRedemptionResponse.refused("HTTP_${it.code}")
                                !it.isSuccessful -> throw IOException("Reward response unavailable")
                                else -> json.decodeFromString(ChannelPointRewardRedemptionResponse.serializer(), it.body.string())
                            }
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    internal fun redemptionBody(channelId: String, reward: ChannelPointRewardOption, text: String?, transactionId: String) = buildJsonObject {
        put("operationName", "RedeemCommunityPointsCustomReward")
        put("query", "mutation RedeemCommunityPointsCustomReward(\$input: RedeemCommunityPointsCustomRewardInput!) { redeemCommunityPointsCustomReward(input: \$input) { error { code } } }")
        putJsonObject("variables") {
            putJsonObject("input") {
                put("channelID", channelId)
                put("rewardID", reward.id)
                put("title", reward.title)
                put("cost", reward.cost)
                put("prompt", reward.prompt)
                put("textInput", text?.takeIf { reward.requiresInput })
                put("transactionID", transactionId)
            }
        }
    }.toString()

    private companion object {
        // Bad request, invalid token, forbidden and rate limited. Other failures may follow execution.
        val REFUSED_BEFORE_RUN = setOf(400, 401, 403, 429)
    }
}

/** Do not inherit debug BODY logging for authenticated reward catalog or message input. */
internal fun OkHttpClient.withoutRewardLogging(): OkHttpClient = newBuilder().apply {
    interceptors().removeAll { it is HttpLoggingInterceptor }
    networkInterceptors().removeAll { it is HttpLoggingInterceptor }
}.build()
