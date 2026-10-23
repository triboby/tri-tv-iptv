package com.tritv.iptv.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tritv.iptv.data.Channel
import com.tritv.iptv.ui.EyebrowStyle
import com.tritv.iptv.ui.TriTvColors
import com.tritv.iptv.ui.glassPanel
import kotlinx.coroutines.delay

private const val DEFAULT_USER_AGENT = "TriTV/1.0 (Linux; Android) ExoPlayer"

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    channelNumber: Int,
    onZap: (Int) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    val attempts = remember(channel) { playbackAttempts(channel.sources) }
    var attemptIndex by remember(channel) { mutableIntStateOf(0) }
    var error by remember(channel) { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(true) }
    val attempt = attempts[attemptIndex]

    // Keyed on channel so the listener sees this channel's attempt state.
    DisposableEffect(player, channel) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }

            override fun onPlayerError(e: PlaybackException) {
                if (attemptIndex + 1 < attempts.size) {
                    attemptIndex++
                } else {
                    error = "所有线路都无法播放：${e.errorCodeName}"
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(channel, attempt) {
        val source = channel.sources[attempt.sourceIndex]
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(source.userAgent ?: DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
        source.referrer?.let { httpFactory.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder()
            .setUri(source.url)
            .apply { attempt.mimeType?.let { setMimeType(it) } }
            .build()
        player.setMediaSource(DefaultMediaSourceFactory(httpFactory).createMediaSource(item))
        player.prepare()
    }

    LaunchedEffect(channel, attempt.sourceIndex) {
        showInfo = true
        delay(4_000)
        showInfo = false
    }

    /** Jumps to the first attempt of the previous (-1) or next (+1) source line. */
    fun switchSource(delta: Int) {
        val count = channel.sources.size
        if (count < 2) return
        val target = Math.floorMod(attempt.sourceIndex + delta, count)
        error = null
        attemptIndex = attempts.indexOfFirst { it.sourceIndex == target }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp -> { onZap(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { onZap(1); true }
                    Key.DirectionLeft -> { switchSource(-1); true }
                    Key.DirectionRight -> { switchSource(1); true }
                    Key.DirectionCenter, Key.Enter, Key.Info -> { showInfo = !showInfo; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    isFocusable = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(false)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = showInfo || error != null,
            enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 5 },
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(48.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .glassPanel()
                    .padding(horizontal = 28.dp, vertical = 18.dp),
            ) {
                Text(
                    "%03d".format(channelNumber),
                    style = MaterialTheme.typography.displaySmall,
                    color = TriTvColors.Text,
                )
                Box(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .width(1.dp)
                        .height(48.dp)
                        .background(TriTvColors.GlassStroke),
                )
                Column {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TriTvColors.Text,
                    )
                    val line = if (channel.sources.size > 1) {
                        "线路 ${attempt.sourceIndex + 1}/${channel.sources.size}"
                    } else {
                        null
                    }
                    val status = error ?: listOfNotNull(
                        channel.group,
                        line,
                        "缓冲中…".takeIf { buffering },
                    ).joinToString("  ·  ")
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (error != null) TriTvColors.Error else TriTvColors.TextMid,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "▲▼ 换台   ◀▶ 换线路   OK 信息   返回 频道列表",
                        style = EyebrowStyle.copy(fontSize = 11.sp),
                        color = TriTvColors.TextDim,
                    )
                }
            }
        }
    }
}
