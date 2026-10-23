package com.tritv.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.tritv.iptv.UiState
import com.tritv.iptv.epg.Programme
import java.text.SimpleDateFormat
import java.util.Locale

private const val MINUTE = 60_000L
private const val HALF_HOUR = 30 * MINUTE
private const val WINDOW = 4 * HALF_HOUR

/** Rows shown above the selected one when the list scrolls. */
private const val ROWS_ABOVE = 2

/**
 * The programme guide: a row per channel of the current list, two hours across, with a line at
 * the current time. One cursor (a channel and a moment) moves with the D-pad: Up/Down change the
 * channel, Left/Right step to the previous or next programme, scrolling the window by half hours.
 * Menu jumps back to now; OK on a programme that is on plays the channel.
 */
@Composable
fun GuideScreen(state: UiState, onPlay: (Int) -> Unit, onBack: () -> Unit) {
    val rows = state.visibleIndices
    val now = rememberNow()
    var row by remember { mutableIntStateOf(rows.indexOf(state.focusIndex).coerceAtLeast(0)) }
    var cursor by remember { mutableLongStateOf(now) }
    var windowStart by remember { mutableLongStateOf(floorHalfHour(now)) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(row) { listState.animateScrollToItem((row - ROWS_ABOVE).coerceAtLeast(0)) }

    fun programmesAt(r: Int): List<Programme> =
        rows.getOrNull(r)?.let { state.programmesOf(state.channels[it]) }.orEmpty()

    /** Puts the cursor at [time] and scrolls the window so it shows. */
    fun moveCursor(time: Long) {
        cursor = time.coerceIn(now - 6 * 60 * MINUTE, now + 3 * 24 * 60 * MINUTE)
        if (cursor < windowStart) windowStart = floorHalfHour(cursor)
        if (cursor >= windowStart + WINDOW) windowStart = floorHalfHour(cursor) - WINDOW + HALF_HOUR
    }

    val selected = programmesAt(row).firstOrNull { it.isOn(cursor) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TriTvColors.Black)
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val list = programmesAt(row)
                when (event.key) {
                    Key.DirectionUp -> { if (row > 0) row--; true }
                    Key.DirectionDown -> { if (row < rows.lastIndex) row++; true }
                    Key.DirectionLeft -> {
                        val current = list.firstOrNull { it.isOn(cursor) }
                        val prev = list.lastOrNull { it.stop <= (current?.start ?: cursor) }
                        // Land inside the previous programme, near its end so the window moves little.
                        moveCursor(prev?.let { maxOf(it.start, it.stop - HALF_HOUR) } ?: (cursor - HALF_HOUR))
                        true
                    }
                    Key.DirectionRight -> {
                        val current = list.firstOrNull { it.isOn(cursor) }
                        val next = list.firstOrNull { it.start >= (current?.stop ?: (cursor + 1)) }
                        moveCursor(next?.start ?: (cursor + HALF_HOUR))
                        true
                    }
                    Key.Menu -> {
                        cursor = now
                        windowStart = floorHalfHour(now)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        val p = list.firstOrNull { it.isOn(cursor) }
                        // Live TV can only show what is on now.
                        if (p == null || p.isOn(now)) rows.getOrNull(row)?.let(onPlay)
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        TopBar {
            KeyCap("返回")
            Text("频道列表", style = TriTvType.Small, color = TriTvColors.TextMuted)
            Spacer(Modifier.width(12.dp))
        }
        GuideNav(windowStart)

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(TriTvColors.Ink)
                .padding(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 16.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val track = maxWidth - HEAD_WIDTH - 8.dp
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimeRuler(windowStart, now, track)
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = false,
                    ) {
                        itemsIndexed(rows, key = { _, index -> index }) { r, index ->
                            GuideRow(
                                state = state,
                                index = index,
                                number = if (state.favoritesSelected) r + 1 else index + 1,
                                windowStart = windowStart,
                                now = now,
                                track = track,
                                focused = r == row,
                                selected = if (r == row) selected else null,
                            )
                        }
                    }
                }
                // The current time, across every row.
                val nowX = HEAD_WIDTH + 8.dp + track * ((now - windowStart).toFloat() / WINDOW)
                if (now in windowStart until windowStart + WINDOW) {
                    Box(
                        Modifier
                            .padding(top = 30.dp)
                            .offset(x = nowX - 1.dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(TriTvColors.SkyBlue),
                    )
                }
            }
            DetailPanel(state, rows.getOrNull(row), selected, now)
        }
    }
}

private val HEAD_WIDTH = 200.dp

private fun floorHalfHour(t: Long) = t - Math.floorMod(t, HALF_HOUR)

private fun hm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(t)

@Composable
private fun GuideNav(windowStart: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(TriTvColors.Ink.copy(alpha = 0.8f))
            .padding(horizontal = ScreenMargin),
    ) {
        Text("节目表", style = TriTvType.Tagline)
        Text(
            SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(windowStart),
            style = TriTvType.Caption,
            color = TriTvColors.TextMuted,
        )
        Spacer(Modifier.weight(1f))
        GuideKeyHint("▲▼", "换频道")
        GuideKeyHint("◀▶", "前后节目")
        GuideKeyHint("菜单", "回到现在")
        GuideKeyHint("OK", "观看")
    }
}

@Composable
private fun GuideKeyHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyCap(key)
        Text(label, style = TriTvType.Small, color = TriTvColors.TextMuted)
    }
}

/** Half-hour marks over the programme track, the current time as a blue pill. */
@Composable
private fun TimeRuler(windowStart: Long, now: Long, track: Dp) {
    Box(Modifier.fillMaxWidth().height(24.dp)) {
        for (i in 0 until 4) {
            Text(
                hm(windowStart + i * HALF_HOUR),
                style = TriTvType.Small.tabular(),
                color = TriTvColors.TextDim,
                modifier = Modifier
                    .offset(x = HEAD_WIDTH + 8.dp + track * (i / 4f))
                    .align(Alignment.CenterStart),
            )
        }
        if (now in windowStart until windowStart + WINDOW) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = HEAD_WIDTH + 8.dp + track * ((now - windowStart).toFloat() / WINDOW) - 24.dp)
                    .width(48.dp)
                    .height(20.dp)
                    .align(Alignment.CenterStart)
                    .background(TriTvColors.SkyBlue, Radius.Pill),
            ) {
                Text(hm(now), style = TriTvType.Small.copy(fontWeight = FontWeight.SemiBold).tabular(), color = Color.Black)
            }
        }
    }
}

@Composable
private fun GuideRow(
    state: UiState,
    index: Int,
    number: Int,
    windowStart: Long,
    now: Long,
    track: Dp,
    focused: Boolean,
    selected: Programme?,
) {
    val channel = state.channels[index]
    val programmes = state.programmesOf(channel).filter { it.stop > windowStart && it.start < windowStart + WINDOW }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .width(HEAD_WIDTH)
                .fillMaxHeight()
                .background(if (focused) TriTvColors.Tile else Color.Transparent, Radius.Small)
                .padding(start = 4.dp, end = 12.dp),
        ) {
            Text(
                "%03d".format(number),
                style = TriTvType.Caption.tabular(),
                color = TriTvColors.TextMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.width(32.dp),
            )
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                if (state.isFavorite(channel)) StarIcon(12.dp)
            }
            Text(channel.name, style = TriTvType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(track).fillMaxHeight().clipToBounds()) {
            if (programmes.isEmpty()) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (focused) Modifier.background(TriTvColors.Focus, Radius.Small)
                            else Modifier.border(1.dp, TriTvColors.Hairline, Radius.Small),
                        )
                        .padding(horizontal = 12.dp),
                ) {
                    Text("暂无节目信息", style = TriTvType.Label, color = if (focused) TriTvColors.Text else TriTvColors.TextDim)
                }
            }
            for (p in programmes) {
                val start = maxOf(p.start, windowStart)
                val stop = minOf(p.stop, windowStart + WINDOW)
                val x = track * ((start - windowStart).toFloat() / WINDOW)
                val w = track * ((stop - start).toFloat() / WINDOW)
                val isSelected = p == selected
                val bg = when {
                    isSelected -> TriTvColors.Focus
                    p.isOn(now) -> TriTvColors.TileRaised
                    else -> TriTvColors.Tile
                }
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .offset(x = x + 2.dp)
                        .width((w - 4.dp).coerceAtLeast(2.dp))
                        .fillMaxHeight()
                        .background(bg, Radius.Small)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        (if (p.start < windowStart) "‹ " else "") + p.title,
                        style = TriTvType.Label,
                        color = if (isSelected || p.isOn(now)) TriTvColors.Text else TriTvColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The selected programme: title, channel, day and time, whether it is on, and its description. */
@Composable
private fun DetailPanel(state: UiState, index: Int?, programme: Programme?, now: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(TriTvColors.Tile, Radius.Card)
            .padding(start = 24.dp, end = 20.dp),
    ) {
        val channel = index?.let { state.channels[it] }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                programme?.title ?: channel?.name.orEmpty(),
                style = TriTvType.Tagline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val dim = TriTvColors.TextMuted
                if (channel != null && programme != null) {
                    Text(channel.name, style = TriTvType.Caption, color = dim, maxLines = 1)
                    Dot()
                }
                if (programme != null) {
                    Text(
                        SimpleDateFormat("M月d日", Locale.CHINA).format(programme.start) + " " + timeRange(programme),
                        style = TriTvType.Caption.tabular(),
                        color = dim,
                    )
                    if (programme.isOn(now)) {
                        Dot()
                        Text("正在播", style = TriTvType.Caption, color = TriTvColors.SkyBlue)
                    }
                } else {
                    Text("暂无节目信息", style = TriTvType.Caption, color = dim)
                }
            }
            programme?.desc?.let {
                Text(it, style = TriTvType.Small, color = TriTvColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (programme == null || programme.isOn(now)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(40.dp)
                    .background(TriTvColors.Action, Radius.Pill)
                    .padding(horizontal = 22.dp),
            ) {
                Text("OK 观看", style = TriTvType.Label, color = TriTvColors.Text)
            }
        }
    }
}
