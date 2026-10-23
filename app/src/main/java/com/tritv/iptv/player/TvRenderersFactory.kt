package com.tritv.iptv.player

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Decodes audio with FFmpeg first, so MP2 and AC-3 tracks common on Chinese IPTV play
 * on TVs whose chips lack those decoders (they otherwise give picture with no sound).
 * Video stays on the hardware decoders: software video would not keep up on a TV.
 */
@OptIn(UnstableApi::class)
internal class TvRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) = super.buildVideoRenderers(
        context,
        EXTENSION_RENDERER_MODE_OFF,
        mediaCodecSelector,
        enableDecoderFallback,
        eventHandler,
        eventListener,
        allowedVideoJoiningTimeMs,
        out,
    )
}
