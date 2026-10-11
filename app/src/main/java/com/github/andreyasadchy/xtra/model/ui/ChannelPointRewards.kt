package com.github.andreyasadchy.xtra.model.ui

/** A viewer-facing custom reward. Automatic/emote rewards need separate flows. */
data class ChannelPointRewardOption(
    val id: String,
    val title: String,
    val cost: Int,
    val prompt: String?,
    val requiresInput: Boolean,
    val available: Boolean,
)

data class ChannelPointRewardCatalog(val balance: Int, val rewards: List<ChannelPointRewardOption>)
