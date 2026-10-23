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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.tritv.iptv.epg.Programme
import com.tritv.iptv.epg.nowAndNext
import com.tritv.iptv.ui.ClockText
import com.tritv.iptv.ui.Dot
import com.tritv.iptv.ui.GroupCaption
import com.tritv.iptv.ui.KeyCap
import com.tritv.iptv.ui.ProgressBar
import com.tritv.iptv.ui.Radius
import com.tritv.iptv.ui.ScreenMargin
import com.tritv.iptv.ui.StarIcon
import com.tritv.iptv.ui.TriTvColors
import com.tritv.iptv.ui.TriTvType
import com.tritv.iptv.ui.rememberNow
import com.tritv.iptv.ui.tabular
import com.tritv.iptv.ui.timeRange
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

private const val DEFAULT_USER_AGENT = "TriTV/1.0 (Linux; Android) ExoPlayer"

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    channelNumber: Int,
    programmes: List<Programme>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenGuide: () -> Unit,
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
    // Brief "已加入收藏 / 已取消收藏" note after the viewer toggles the favourite.
    var favoriteNote by remember { mutableStateOf<String?>(null) }
    // Set once a held OK has toggled the favourite, so its release does not also toggle the info bar.
    var okHeld by remember { mutableStateOf(false) }
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

    LaunchedEffect(favoriteNote) {
        if (favoriteNote == null) return@LaunchedEffect
        delay(2_000)
        favoriteNote = null
    }

    fun toggleFavorite() {
        favoriteNote = if (isFavorite) "已取消收藏" else "已加入收藏"
        onToggleFavorite()
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
                // OK toggles the info bar on release; held down, it toggles the favourite instead.
                if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (event.nativeKeyEvent.repeatCount > 0 && !okHeld) {
                            okHeld = true
                            toggleFavorite()
                        }
                    } else if (event.type == KeyEventType.KeyUp) {
                        if (!okHeld) showInfo = !showInfo
                        okHeld = false
                    }
                    return@onKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp -> { onZap(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { onZap(1); true }
                    Key.DirectionLeft -> { switchSource(-1); true }
                    Key.DirectionRight -> { switchSource(1); true }
                    Key.Info -> { showInfo = !showInfo; true }
                    Key.Menu -> { toggleFavorite(); true }
                    Key.Guide -> { onOpenGuide(); true }
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
            visible = favoriteNote != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 27.dp, start = ScreenMargin)
                    .height(36.dp)
                    .background(TriTvColors.Ink.copy(alpha = 0.8f), Radius.Pill)
                    .padding(start = 12.dp, end = 16.dp),
            ) {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { StarIcon(filled = isFavorite) }
                Text(favoriteNote.orEmpty(), style = TriTvType.Caption)
            }
        }

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
            val now = rememberNow()
            val (current, next) = programmes.nowAndNext(now)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TriTvColors.Ink.copy(alpha = 0.85f))
                    .padding(start = ScreenMargin, end = ScreenMargin, top = 18.dp, bottom = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        "%03d".format(channelNumber),
                        style = TriTvType.PlayerNumber.tabular(),
                        modifier = Modifier.width(88.dp),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = if (current != null || next != null) Modifier.width(260.dp) else Modifier.weight(1f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(channel.name, style = TriTvType.Tagline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (isFavorite) StarIcon(16.dp)
                        }
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
                    if (current != null || next != null) {
                        NowAndNext(current, next, now, Modifier.weight(1f))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    KeyHint("▲▼", "换台")
                    KeyHint("◀▶", "换线路")
                    KeyHint("OK", "信息")
                    KeyHint("菜单", if (isFavorite) "取消收藏" else "收藏")
                    if (programmes.isNotEmpty()) KeyHint("指南", "节目表")
                    KeyHint("返回", "列表")
                }
            }
        }
    }
}

/** "正在播" with title, time and progress, and "下一个" with its start time. */
@Composable
private fun NowAndNext(current: Programme?, next: Programme?, now: Long, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("正在播", style = TriTvType.LabelStrong, color = TriTvColors.SkyBlue)
                Text(current.title, style = TriTvType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val left = ((current.stop - now) / 60_000).coerceAtLeast(0)
                Text(
                    timeRange(current) + " · 剩 " + "%d:%02d".format(left / 60, left % 60),
                    style = TriTvType.Caption.tabular(),
                    color = TriTvColors.TextMuted,
                )
            }
            ProgressBar(current.progress(now), Modifier.fillMaxWidth())
        }
        if (next != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("下一个", style = TriTvType.LabelStrong, color = TriTvColors.TextMuted)
                Text(hm(next.start), style = TriTvType.Caption.tabular(), color = TriTvColors.TextMuted)
                Text(next.title, style = TriTvType.Caption, color = TriTvColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun hm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(t)

@Composable
private fun KeyHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyCap(key)
        Text(label, style = TriTvType.Small, color = TriTvColors.TextMuted)
    }
}
