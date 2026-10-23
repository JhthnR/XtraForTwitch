package com.github.andreyasadchy.xtra.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** Keep manual setMediaItem quality/audio switches on the current source's parser and HTTP factory. */
@OptIn(UnstableApi::class)
internal class ConfiguredMediaSourceFactory(private val fallback: MediaSource.Factory) : MediaSource.Factory {
    private var configured: MediaSource.Factory? = null

    fun configure(factory: MediaSource.Factory?) { configured = factory }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        (configured ?: fallback).createMediaSource(mediaItem)

    override fun getSupportedTypes(): IntArray = fallback.supportedTypes

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        fallback.setDrmSessionManagerProvider(provider)
        configured?.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        fallback.setLoadErrorHandlingPolicy(policy)
        configured?.setLoadErrorHandlingPolicy(policy)
        return this
    }
}
