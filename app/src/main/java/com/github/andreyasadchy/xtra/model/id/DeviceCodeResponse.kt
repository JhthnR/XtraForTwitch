package com.github.andreyasadchy.xtra.model.id

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DeviceCodeResponse(
    @SerialName("device_code")
    val deviceCode: String? = null,
    @SerialName("user_code")
    val userCode: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int = 1800,
    @SerialName("interval")
    val interval: Int = 5,
)
