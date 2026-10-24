package com.tritv.iptv.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.tritv.iptv.data.Channel
import com.tritv.iptv.data.ChannelCategory
import com.tritv.iptv.data.LineStat
import com.tritv.iptv.data.PlaylistEntry
import com.tritv.iptv.epg.Programme
import com.tritv.iptv.epg.nowAndNext
import com.tritv.iptv.ui.ChannelJumpPill
import com.tritv.iptv.ui.ChannelLogo
import com.tritv.iptv.ui.ClockText
import com.tritv.iptv.ui.Dot
import com.tritv.iptv.ui.EnterEasing
import com.tritv.iptv.ui.ExitEasing
import com.tritv.iptv.ui.GroupCaption
import com.tritv.iptv.ui.KeyCap
import com.tritv.iptv.ui.ProgressBar
import com.tritv.iptv.ui.Radius
import com.tritv.iptv.ui.ScreenMargin
import com.tritv.iptv.ui.Spinner
import com.tritv.iptv.ui.StarIcon
import com.tritv.iptv.ui.TriTvColors
import com.tritv.iptv.ui.TriTvType
import com.tritv.iptv.ui.digitOf
import com.tritv.iptv.ui.rememberLogo
import com.tritv.iptv.ui.rememberNow
import com.tritv.iptv.ui.rememberNumberEntry
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
    /** [channel]'s lines in the order to try them. */
    lines: List<PlaylistEntry>,
    lineStats: Map<String, LineStat>,
    /** Called when a line starts playing (true) or fails (false). */
    onLineResult: (PlaylistEntry, Boolean) -> Unit,
    /** Called when a line the viewer switched to starts playing. */
    onLinePicked: (PlaylistEntry) -> Unit,
    channelNumber: Int,
    programmes: List<Programme>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenGuide: () -> Unit,
    /** Index of the channel that many steps away in the current list, for the zap preview. */
    zapTarget: (Int) -> Int?,
    /** Name of the channel with this list number (1-based), or null if there is none. */
    channelName: (Int) -> String?,
    onZap: (Int) -> Unit,
    onPlayNumber: (Int) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context, TvRenderersFactory(context)).build().apply { playWhenReady = true }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Leaving the app with Home keeps this screen alive in the background, so stop the stream
    // (sound and download) while the app is hidden and reconnect the same line on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.stop()
                // Back to the live edge: the old position may have left the live window, which
                // would fail as an error and wrongly move on to the next line.
                Lifecycle.Event.ON_START -> if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                    player.seekToDefaultPosition()
                    player.prepare()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val attempts = remember(channel, lines) { playbackAttempts(lines) }
    var attemptIndex by remember(channel) { mutableIntStateOf(0) }
    var error by remember(channel) { mutableStateOf<String?>(null) }
    // First attempt that showed video without decodable audio, kept as a last resort.
    var silentLine by remember(channel) { mutableStateOf<Int?>(null) }
    // Set when the viewer picked the line, or every other line failed: stay on it even if silent.
    var stayOnLine by remember(channel) { mutableStateOf(false) }
    var noAudio by remember(channel) { mutableStateOf(false) }
    // Whether the current attempt has played yet; reset when it changes.
    var ready by remember(channel) { mutableStateOf(false) }
    // Set when the viewer switched lines, so the line is remembered once it plays.
    var pickedByViewer by remember(channel) { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    // Whether any line of this channel has shown a picture yet. After that, switching lines keeps
    // the last frame on screen (dimmed) instead of going black.
    var playedOnce by remember(channel) { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(true) }
    // Brief "已加入收藏 / 已取消收藏" note after the viewer toggles the favourite.
    var favoriteNote by remember { mutableStateOf<String?>(null) }
    // Set once a held OK has toggled the favourite, so its release does not also toggle the info bar.
    var okHeld by remember { mutableStateOf(false) }
    // Channel steps pressed in quick succession, applied together once the presses pause, so a
    // burst of Up/Down does not restart the player for every channel on the way.
    var pendingZap by remember { mutableIntStateOf(0) }
    val numberEntry = rememberNumberEntry(onPlayNumber)
    val attempt = attempts[attemptIndex]
    val attemptStartedAt = remember(channel, attemptIndex) { System.currentTimeMillis() }

    // Keyed on channel so the listener sees this channel's attempt state.
    DisposableEffect(player, channel, lines) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    error = null
                    if (!ready) {
                        ready = true
                        playedOnce = true
                        val line = lines[attempts[attemptIndex].sourceIndex]
                        onLineResult(line, true)
                        if (pickedByViewer) onLinePicked(line)
                    }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                onLineResult(lines[attempts[attemptIndex].sourceIndex], false)
                val next = nextAttempt(attemptIndex, attempts.size, silentLine)
                if (next == null) {
                    error = "所有线路都无法播放：" + playbackErrorReason(e.errorCode)
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

    // Probes of this channel's lines start with it and answer well before the player gives up
    // on a dead line, so act on them: leave a line found dead, or one still loading after a
    // few seconds when another line is known to work.
    val lineUrls = remember(lines) { lines.map { it.url } }
    val lineStat = lineStats[lineUrls[attempt.sourceIndex]]
    val channelStats = lineUrls.map { lineStats[it] }
    LaunchedEffect(attemptIndex, channelStats) {
        fun check(): Boolean {
            if (ready || stayOnLine) return true
            val next = probeSwitch(attempts, lineUrls, lineStats, attemptIndex, attemptStartedAt, System.currentTimeMillis())
                ?: return false
            attemptIndex = next
            return true
        }
        if (check()) return@LaunchedEffect
        val wait = attemptStartedAt + SLOW_START_MS - System.currentTimeMillis()
        if (wait > 0) {
            delay(wait)
            check()
        }
    }

    LaunchedEffect(channel, attempt) {
        ready = false
        val source = lines[attempt.sourceIndex]
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(source.userAgent ?: DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(6_000)
            .setReadTimeoutMs(10_000)
        source.referrer?.let { httpFactory.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder()
            .setUri(source.url)
            .apply { attempt.mimeType?.let { setMimeType(it) } }
            .build()
        // Until a line has played, give up on it after one retry so the next line gets its turn;
        // once playing, ride out short network hiccups instead of switching lines.
        val retries = object : DefaultLoadErrorHandlingPolicy() {
            override fun getMinimumLoadableRetryCount(dataType: Int) = if (ready) 3 else 1
        }
        player.setMediaSource(
            DefaultMediaSourceFactory(httpFactory).setLoadErrorHandlingPolicy(retries).createMediaSource(item),
        )
        player.prepare()
    }

    // The loading ring stays up from the start of buffering until the line plays, whatever the info
    // bar does. It waits 0.4 s before appearing so a line that starts quickly does not flash it.
    val loading = error == null && (!ready || buffering)
    var showLoader by remember { mutableStateOf(false) }
    var loadingSince by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(loading, channel, attemptIndex) {
        if (!loading) {
            showLoader = false
            return@LaunchedEffect
        }
        loadingSince = System.currentTimeMillis()
        if (!showLoader) {
            delay(LOADER_DELAY_MS)
            showLoader = true
        }
    }
    val dim by animateFloatAsState(
        targetValue = if (playedOnce && !ready && error == null) SWITCH_DIM else 0f,
        animationSpec = tween(200),
        label = "dim",
    )

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

    LaunchedEffect(pendingZap) {
        if (pendingZap == 0) return@LaunchedEffect
        delay(350)
        val delta = pendingZap
        pendingZap = 0
        onZap(delta)
    }

    fun toggleFavorite() {
        favoriteNote = if (isFavorite) "已取消收藏" else "已加入收藏"
        onToggleFavorite()
    }

    /** Jumps to the first attempt of the previous (-1) or next (+1) source line. */
    fun switchSource(delta: Int) {
        val count = lines.size
        if (count < 2) return
        val target = Math.floorMod(attempt.sourceIndex + delta, count)
        error = null
        stayOnLine = true
        pickedByViewer = true
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
                // Digits type a channel number; consume both halves so the TV does not switch input.
                digitOf(event.key)?.let { digit ->
                    if (event.type == KeyEventType.KeyDown) numberEntry.type(digit)
                    return@onKeyEvent true
                }
                if ((event.key == Key.DirectionCenter || event.key == Key.Enter) && numberEntry.active) {
                    if (event.type == KeyEventType.KeyUp) numberEntry.commit()
                    return@onKeyEvent true
                }
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
                    Key.DirectionUp, Key.ChannelUp -> { pendingZap--; true }
                    Key.DirectionDown, Key.ChannelDown -> { pendingZap++; true }
                    Key.DirectionLeft -> { switchSource(-1); true }
                    Key.DirectionRight -> { switchSource(1); true }
                    Key.Info -> { showInfo = !showInfo; true }
                    // ≡ is on every BRAVIA remote; few have a Guide key.
                    Key.Menu, Key.Guide -> { onOpenGuide(); true }
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
                    // Live TV gets no remote input for long stretches; without this the TV's
                    // screensaver starts mid-programme. Dropped when the player screen leaves.
                    keepScreenOn = true
                    this.player = player
                }
            },
            // A new channel starts on black; another line of the same channel keeps the last frame.
            update = { it.setKeepContentOnPlayerReset(playedOnce) },
            modifier = Modifier.fillMaxSize(),
        )

        if (dim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dim }
                    .background(Color.Black),
            )
        }

        AnimatedVisibility(
            visible = showLoader && loading,
            enter = fadeIn(tween(240, easing = EnterEasing)) + scaleIn(tween(240, easing = EnterEasing), initialScale = 0.96f),
            exit = fadeOut(tween(200, easing = ExitEasing)) + scaleOut(tween(200, easing = ExitEasing), targetScale = 0.98f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            // Moved off the first line, by the viewer or because a line failed.
            val switching = pickedByViewer || attemptIndex > 0
            val lineLabel = if (lines.size > 1) "线路 ${attempt.sourceIndex + 1}/${lines.size}" else null
            BufferingIndicator(
                caption = when {
                    ready -> "缓冲中"
                    switching && lineLabel != null -> "正在换到$lineLabel"
                    else -> "正在连接"
                },
                detail = listOfNotNull(channel.name, lineLabel.takeIf { !switching }).joinToString(" · "),
                since = loadingSince,
            )
        }

        // Where a burst of Up/Down or the typed digits will land.
        val jumpNumber = when {
            numberEntry.active -> numberEntry.digits
            pendingZap != 0 -> zapTarget(pendingZap)?.let { "%03d".format(it + 1) }
            else -> null
        }
        if (jumpNumber != null) {
            ChannelJumpPill(
                number = jumpNumber,
                name = channelName(jumpNumber.toInt()),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
            )
        }

        AnimatedVisibility(
            visible = favoriteNote != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            // Centred: stations put their logo in a top corner.
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 27.dp)
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
                    // The guide makes way for an error, which then gets the full width.
                    val showGuide = (current != null || next != null) && error == null
                    // The logo stands in for the name, as on the channel list; text until one loads.
                    val logo = rememberLogo(channel.logos)
                    if (logo != null) {
                        ChannelLogo(logo, ChannelCategory.label(channel.name), height = 44.dp, maxWidth = 176.dp)
                    } else {
                        Text(
                            ChannelCategory.label(channel.name),
                            style = TriTvType.Tagline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (showGuide) Modifier.width(240.dp) else Modifier.weight(1f),
                        )
                    }
                    if (showGuide) {
                        NowAndNext(current, next, now, Modifier.weight(1f))
                    } else if (logo != null) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                // Status on the left (favourite, group, line, problems), key hints on the right.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isFavorite) StarIcon(14.dp)
                        val err = error
                        if (err != null) {
                            Text(err, style = TriTvType.Caption, color = TriTvColors.Error, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        } else {
                            channel.group?.let { GroupCaption(it, TriTvType.Caption, TriTvColors.TextMuted) }
                            if (lines.size > 1) {
                                if (channel.group != null) Dot()
                                Text(
                                    "线路 ${attempt.sourceIndex + 1}/${lines.size}",
                                    style = TriTvType.Caption,
                                    color = TriTvColors.TextMuted,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                // How fast this line answered its last probes.
                                lineStat?.takeIf { !it.failing }?.latencyMs?.let { ms ->
                                    Dot()
                                    Text("$ms ms", style = TriTvType.Caption.tabular(), color = TriTvColors.TextMuted, maxLines = 1, softWrap = false)
                                }
                            }
                            // Buffering is shown by the ring in the middle of the picture, not here.
                            if (noAudio && !buffering) {
                                Dot()
                                Text(
                                    "无声音：音频格式不支持",
                                    style = TriTvType.Caption,
                                    color = TriTvColors.Error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                        }
                    }
                    KeyHint("▲▼", "换台")
                    KeyHint("◀▶", "换线路")
                    KeyHint("OK", "信息")
                    KeyHint("长按 OK", if (isFavorite) "取消收藏" else "收藏")
                    if (programmes.isNotEmpty()) KeyHint("≡", "节目表")
                    KeyHint("返回", "列表")
                }
            }
        }
    }
}

/**
 * Ring with a caption under it, centred on the picture. From 5 s on the second line says how long
 * it has been waiting, so a slow line does not look frozen.
 */
@Composable
private fun BufferingIndicator(caption: String, detail: String, since: Long) {
    var waited by remember(since) { mutableIntStateOf(0) }
    LaunchedEffect(since) {
        while (true) {
            delay(1_000)
            waited = ((System.currentTimeMillis() - since) / 1_000).toInt()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spinner()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(caption, style = TriTvType.Body)
            Text(
                if (waited >= 5) "仍在连接 · 已等待 $waited 秒" else detail,
                style = TriTvType.Caption.tabular(),
                color = TriTvColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Buffering shorter than this shows no loading ring. */
private const val LOADER_DELAY_MS = 400L

/** How dark the last frame goes while another line of the same channel loads. */
private const val SWITCH_DIM = 0.65f

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
