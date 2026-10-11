package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointRewardRedemptionResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointRewardRedemptionResponse.Outcome
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointRewardsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChannelPointRewardsModelTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val reward = """{"id":"reward","title":"Hello","cost":100,"isUserInputRequired":true,"isEnabled":true,"isPaused":false,"isInStock":true}"""
    private fun catalog(rewards: String = reward, balance: String = "1000") = json.decodeFromString<ChannelPointRewardsResponse>(
        """{"data":{"community":{"channel":{"self":{"communityPoints":{"balance":$balance}},"communityPointsSettings":{"customRewards":[$rewards]}}}}}"""
    ).catalog()
    @Test fun `complete catalog decodes required input availability and balance`() {
        val actual = catalog()!!
        assertEquals(1000, actual.balance)
        assertTrue(actual.rewards.single().requiresInput)
        assertTrue(actual.rewards.single().available)
    }
    @Test fun `missing null or negative balance cannot authorize purchases`() {
        assertNull(catalog(balance = "null"))
        assertNull(catalog(balance = "-1"))
        assertNull(json.decodeFromString<ChannelPointRewardsResponse>("""{"data":{"community":null}}""").catalog())
    }
    @Test fun `missing availability or input requirements fail closed`() {
        listOf("isEnabled", "isPaused", "isInStock", "isUserInputRequired").forEach { field ->
            val partial = reward.replace(Regex("\\\"$field\\\":(true|false),?"), "").replace(",}", "}")
            assertFalse(field, catalog(partial)!!.rewards.single().available)
        }
    }
    @Test fun `disabled paused and out of stock rewards remain visibly unavailable`() {
        listOf(reward.replace("\"isEnabled\":true", "\"isEnabled\":false"), reward.replace("\"isPaused\":false", "\"isPaused\":true"), reward.replace("\"isInStock\":true", "\"isInStock\":false")).forEach {
            assertFalse(catalog(it)!!.rewards.single().available)
        }
    }
    @Test fun `unsupported pricing and malformed rewards cannot be selected`() {
        val bad = listOf(reward.replace("\"cost\":100", "\"cost\":0"), reward.replace("\"id\":\"reward\"", "\"id\":\"\""), reward.replace("\"cost\":100", "\"cost\":100,\"pricingType\":\"BITS\""))
        bad.forEach { assertTrue(catalog(it)!!.rewards.isEmpty()) }
    }
    @Test fun `missing mutation payload is never success`() {
        listOf("{}", """{"data":null}""", """{"data":{"redeemCommunityPointsCustomReward":null}}""").forEach {
            assertEquals(Outcome.UNKNOWN, json.decodeFromString<ChannelPointRewardRedemptionResponse>(it).outcome())
        }
    }
    @Test fun `empty error object is rejected instead of treated as success`() {
        val value = json.decodeFromString<ChannelPointRewardRedemptionResponse>("""{"data":{"redeemCommunityPointsCustomReward":{"error":{}}}}""")
        assertEquals(Outcome.REJECTED, value.outcome())
    }
    @Test fun `duplicate reward identities are unavailable`() {
        assertFalse(catalog("$reward,$reward")!!.rewards.single().available)
    }
    @Test fun `top level GraphQL error is an uncertain outcome`() {
        val value = json.decodeFromString<ChannelPointRewardRedemptionResponse>("""{"errors":[{"message":"execution failed"}]}""")
        assertEquals(Outcome.UNKNOWN, value.outcome())
    }
    @Test fun `failed integrity check is a definite rejection`() {
        val value = json.decodeFromString<ChannelPointRewardRedemptionResponse>("""{"errors":[{"message":"failed integrity check"}],"data":{"redeemCommunityPointsCustomReward":null}}""")
        assertEquals(Outcome.REJECTED, value.outcome())
    }
}
