package com.github.andreyasadchy.xtra.model

data class VideoQuality(
    val name: String? = null,
    val codecs: String? = null,
    val url: String? = null,
    val bitrate: Int? = null,
    val resolution: Int? = null,
    val frameRate: Float? = null,
    val formatId: String? = null,
)
