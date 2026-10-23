package com.tritv.iptv.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.tritv.iptv.data.Channel
import com.tritv.iptv.ui.ClockText
import com.tritv.iptv.ui.Dot
import com.tritv.iptv.ui.GroupCaption
import com.tritv.iptv.ui.KeyCap
import com.tritv.iptv.ui.Radius
import com.tritv.iptv.ui.ScreenMargin
import com.tritv.iptv.ui.TriTvColors
import com.tritv.iptv.ui.TriTvType
import com.tritv.iptv.ui.tabular
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
        ExoPlayer.Builder(context, TvRenderersFactory(context)).build().apply { playWhenReady = true }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    val attempts = remember(channel) { playbackAttempts(channel.sources) }
    var attemptIndex by remember(channel) { mutableIntStateOf(0) }
    var error by remember(channel) { mutableStateOf<String?>(null) }
    // First attempt that showed video without decodable audio, kept as a last resort.
    var silentLine by remember(channel) { mutableStateOf<Int?>(null) }
    // Set when the viewer picked the line, or every other line failed: stay on it even if silent.
    var stayOnLine by remember(channel) { mutableStateOf(false) }
    var noAudio by remember(channel) { mutableStateOf(false) }
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
                val next = nextAttempt(attemptIndex, attempts.size, silentLine)
                if (next == null) {
                    error = "所有线路都无法播放：${e.errorCodeName}"
                } else {
                    stayOnLine = next == silentLine
                    attemptIndex = next
                }
            }

            // A line whose audio no renderer can decode plays silently without raising an
            // error, so treat it as a failure here and try the next line.
            override fun onTracksChanged(tracks: Tracks) {
                noAudio = tracks.containsType(C.TRACK_TYPE_AUDIO) && !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO)
                if (!noAudio || stayOnLine || attemptIndex + 1 >= attempts.size) return
                if (silentLine == null) silentLine = attemptIndex
                attemptIndex++
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
        stayOnLine = true
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
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 27.dp, end = ScreenMargin)
                    .height(36.dp)
                    .background(TriTvColors.Ink.copy(alpha = 0.8f), Radius.Pill)
                    .padding(horizontal = 16.dp),
            ) {
                ClockText(TriTvType.Caption.copy(fontWeight = FontWeight.SemiBold))
            }
        }

        // Full-width frosted bar along the bottom edge.
        AnimatedVisibility(
            visible = showInfo || error != null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(TriTvColors.Ink.copy(alpha = 0.85f))
                    .padding(start = ScreenMargin, end = ScreenMargin, bottom = 8.dp),
            ) {
                Text(
                    "%03d".format(channelNumber),
                    style = TriTvType.PlayerNumber.tabular(),
                    modifier = Modifier.width(88.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(channel.name, style = TriTvType.Tagline, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val err = error
                        if (err != null) {
                            Text(err, style = TriTvType.Caption, color = TriTvColors.Error, maxLines = 1)
                        } else {
                            channel.group?.let { GroupCaption(it, TriTvType.Caption, TriTvColors.TextMuted) }
                            if (channel.sources.size > 1) {
                                if (channel.group != null) Dot()
                                Text(
                                    "线路 ${attempt.sourceIndex + 1}/${channel.sources.size}",
                                    style = TriTvType.Caption,
                                    color = TriTvColors.TextMuted,
                                )
                            }
                            if (buffering) {
                                Dot()
                                Text("缓冲中", style = TriTvType.Caption, color = TriTvColors.SkyBlue)
                            } else if (noAudio) {
                                Dot()
                                Text("无声音：音频格式不支持", style = TriTvType.Caption, color = TriTvColors.Error)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KeyHint("▲▼", "换台")
                    KeyHint("◀▶", "换线路")
                    KeyHint("OK", "信息")
                    KeyHint("返回", "列表")
                }
            }
        }
    }
}

@Composable
private fun KeyHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyCap(key)
        Text(label, style = TriTvType.Small, color = TriTvColors.TextMuted)
    }
}
