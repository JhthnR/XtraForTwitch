package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.ui.ChannelPointRewardCatalog
import com.github.andreyasadchy.xtra.model.ui.ChannelPointRewardOption
import kotlinx.serialization.Serializable

/** Narrow adaptation of thiscallnet/Xtra fbffe56's ChannelPointsContext model. */
@Serializable
class ChannelPointRewardsResponse(val errors: List<Error>? = null, val data: Data? = null) {
    @Serializable class Data(val community: Community? = null)
    @Serializable class Community(val channel: Channel? = null)
    @Serializable class Channel(val self: Self? = null, val communityPointsSettings: Settings? = null)
    @Serializable class Self(val communityPoints: Points? = null)
    @Serializable class Points(val balance: Int? = null)
    @Serializable class Settings(val customRewards: List<Reward?>? = null)
    @Serializable class Reward(
        val id: String? = null,
        val title: String? = null,
        val cost: Int? = null,
        val prompt: String? = null,
        val pricingType: String? = null,
        val isUserInputRequired: Boolean? = null,
        val isEnabled: Boolean? = null,
        val isPaused: Boolean? = null,
        val isInStock: Boolean? = null,
    )

    fun catalog(): ChannelPointRewardCatalog? {
        if (!errors.isNullOrEmpty()) return null
        val channel = data?.community?.channel ?: return null
        val balance = channel.self?.communityPoints?.balance?.takeIf { it >= 0 } ?: return null
        val rewards = channel.communityPointsSettings?.customRewards ?: return null
        return ChannelPointRewardCatalog(balance, rewards.mapNotNull { reward ->
            reward ?: return@mapNotNull null
            val id = reward.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = reward.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cost = reward.cost?.takeIf { it > 0 } ?: return@mapNotNull null
            if (!reward.pricingType.isNullOrBlank() && !reward.pricingType.equals("POINTS", true)) return@mapNotNull null
            ChannelPointRewardOption(
                id, title, cost, reward.prompt, reward.isUserInputRequired == true,
                // Missing availability/input fields cannot authorize spending.
                reward.isEnabled == true && reward.isPaused == false && reward.isInStock == true && reward.isUserInputRequired != null,
            )
        }.groupBy { it.id }.values.map { duplicates ->
            if (duplicates.size == 1) duplicates.single() else duplicates.first().copy(available = false)
        }.sortedWith(compareBy<ChannelPointRewardOption> { it.cost }.thenBy { it.title }))
    }
}

@Serializable
class ChannelPointRewardRedemptionResponse(val errors: List<Error>? = null, val data: Data? = null) {
    @Serializable class Data(val redeemCommunityPointsCustomReward: Payload? = null)
    @Serializable class Payload(val error: RedemptionError?)
    @Serializable class RedemptionError(val code: String? = null)

    // A present error object with no code is still an error, never success.
    fun outcome(): Outcome = when {
        data?.redeemCommunityPointsCustomReward?.error != null -> Outcome.REJECTED
        // Twitch refuses an unverified client before running the mutation, so nothing was spent.
        errors?.any { it.message == "failed integrity check" } == true -> Outcome.REJECTED
        !errors.isNullOrEmpty() -> Outcome.UNKNOWN
        data?.redeemCommunityPointsCustomReward == null -> Outcome.UNKNOWN
        else -> Outcome.SUCCESS
    }

    enum class Outcome { SUCCESS, REJECTED, UNKNOWN }

    companion object {
        /** Twitch refused the request before running the mutation. */
        fun refused(code: String) = ChannelPointRewardRedemptionResponse(data = Data(Payload(RedemptionError(code))))
    }
}
