package com.tritv.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.tritv.iptv.UiState
import com.tritv.iptv.epg.Programme
import com.tritv.iptv.epg.nowAndNext
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChannelListScreen(
    state: UiState,
    onPlay: (Int) -> Unit,
    onSelectGroup: (String?) -> Unit,
    onSelectFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onStartMove: (String) -> Unit,
    onMove: (Int) -> Unit,
    onFinishMove: (cancel: Boolean) -> Unit,
    onPlayNumber: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val visible = state.visibleIndices
    var focusedIndex by remember { mutableIntStateOf(state.focusIndex) }
    // Channel key whose long-press menu is open.
    var menuFor by remember { mutableStateOf<String?>(null) }
    val now = rememberNow()
    val moving = state.movingFavorite

    val numberEntry = rememberNumberEntry(onPlayNumber)

    BackHandler(enabled = moving != null) { onFinishMove(true) }

    // Put focus back on the last played channel when the list (re)appears.
    LaunchedEffect(state.channels, state.focusIndex, state.selectedGroup, state.favoritesSelected) {
        val pos = visible.indexOf(state.focusIndex)
        if (pos < 0) return@LaunchedEffect
        listState.scrollToItem(pos)
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Digits type a channel number and play it; consumed so the TV does not switch input.
            .onPreviewKeyEvent { event ->
                digitOf(event.key)?.let { digit ->
                    if (event.type == KeyEventType.KeyDown) numberEntry.type(digit)
                    return@onPreviewKeyEvent true
                }
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (ok && numberEntry.active) {
                    if (event.type == KeyEventType.KeyUp) numberEntry.commit()
                    return@onPreviewKeyEvent true
                }
                false
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TriTvColors.Black),
        ) {
            TopBar()
            SubNav(state, onOpenSettings, onOpenGuide)

            if (state.channels.isEmpty()) {
                EmptyHint(loading = state.loading, onOpenSettings = onOpenSettings)
                return@Column
            }

            Hero(state, focusedIndex, now)

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(TriTvColors.Ink)
                    .padding(horizontal = ScreenMargin, vertical = 20.dp),
            ) {
                if (state.groups.isNotEmpty()) {
                    // Scrolls when there are more groups than fit; the bottom padding keeps the last chip clear of the edge.
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        modifier = Modifier
                            .width(184.dp)
                            .fillMaxHeight(),
                    ) {
                        if (state.favorites.isNotEmpty()) {
                            item(key = "__favorites__") {
                                GroupChip(FAVORITES_LABEL, state.favorites.size, state.favoritesSelected, onClick = onSelectFavorites)
                            }
                        }
                        item(key = "__all__") {
                            GroupChip(ALL_LABEL, state.channels.size, !state.favoritesSelected && state.selectedGroup == null) {
                                onSelectGroup(null)
                            }
                        }
                        items(state.groups, key = { it }) { group ->
                            GroupChip(
                                label = remember(group) { groupLabel(group) },
                                count = state.channels.count { it.group == group },
                                selected = !state.favoritesSelected && state.selectedGroup == group,
                            ) { onSelectGroup(group) }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(TriTvColors.Tile, Radius.Card)
                        // While a favourite is being moved, Up/Down move it and OK puts it down.
                        .onPreviewKeyEvent { event ->
                            if (moving == null) return@onPreviewKeyEvent false
                            val down = event.type == KeyEventType.KeyDown
                            when (event.key) {
                                Key.DirectionUp -> { if (down) onMove(-1); true }
                                Key.DirectionDown -> { if (down) onMove(1); true }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (!down) onFinishMove(false); true }
                                Key.DirectionLeft, Key.DirectionRight -> true
                                else -> false
                            }
                        },
                ) {
                    itemsIndexed(visible, key = { _, index -> index }) { _, index ->
                        val channel = state.channels[index]
                        ChannelRow(
                            state = state,
                            index = index,
                            number = index + 1,
                            now = now,
                            moving = channel.stableKey == moving,
                            onClick = { onPlay(index) },
                            onLongClick = { menuFor = channel.stableKey },
                            modifier = Modifier
                                .onFocusChanged { if (it.isFocused) focusedIndex = index }
                                .onKeyEvent { event ->
                                    // Menu adds or removes the focused channel from the favourites.
                                    if (event.key == Key.Menu && moving == null) {
                                        if (event.type == KeyEventType.KeyUp) onToggleFavorite(channel.stableKey)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .then(if (index == state.focusIndex) Modifier.focusRequester(focusRequester) else Modifier),
                        )
                    }
                    if (state.favoritesSelected && moving == null) {
                        items(state.missingFavorites, key = { "missing:$it" }) { key ->
                            MissingFavoriteRow(key, onLongClick = { menuFor = key })
                        }
                    }
                }

                // Keep the favourite being moved in view as it travels.
                LaunchedEffect(moving, state.favorites) {
                    if (moving == null) return@LaunchedEffect
                    val pos = visible.indexOfFirst { state.channels[it].stableKey == moving }
                    if (pos >= 0) listState.animateScrollToItem((pos - 2).coerceAtLeast(0))
                }
            }
        }

        if (numberEntry.active) {
            ChannelJumpPill(
                number = numberEntry.digits,
                name = state.channels.getOrNull(numberEntry.digits.toInt() - 1)?.name,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 104.dp, end = ScreenMargin),
            )
        }
    }

    menuFor?.let { key ->
        val channel = state.channels.firstOrNull { it.stableKey == key }
        ChannelMenu(
            title = channel?.name ?: key,
            isFavorite = key in state.favorites,
            canMove = state.favoritesSelected && key in state.favorites && channel != null,
            onToggleFavorite = { onToggleFavorite(key) },
            onMove = { onStartMove(key) },
            onDismiss = { menuFor = null },
        )
    }
}

private val FAVORITES_LABEL = GroupLabel("⭐", "收藏")
private val ALL_LABEL = GroupLabel(null, "全部")

/** Long-press menu for a channel: add to or remove from the favourites, and move within them. */
@Composable
private fun ChannelMenu(
    title: String,
    isFavorite: Boolean,
    canMove: Boolean,
    onToggleFavorite: () -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .width(280.dp)
                .background(TriTvColors.TileRaised, Radius.Card)
                .padding(8.dp),
        ) {
            Text(
                title,
                style = TriTvType.Small,
                color = TriTvColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            )
            MenuItem(
                label = if (isFavorite) "取消收藏" else "加入收藏",
                icon = { StarIcon(16.dp, filled = !isFavorite) },
                onClick = { onToggleFavorite(); onDismiss() },
                modifier = Modifier.focusRequester(first),
            )
            if (canMove) {
                MenuItem(label = "移动", icon = { MoveIcon() }, onClick = { onMove(); onDismiss() })
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun MenuItem(label: String, icon: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusRow(
        onClick = onClick,
        gap = 10.dp,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, style = TriTvType.Label)
    }
}

/** A favourite whose channel the playlist no longer has: dimmed, not playable, removable from its menu. */
@Composable
private fun MissingFavoriteRow(key: String, onLongClick: () -> Unit) {
    FocusRow(
        onClick = {},
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        val dim = LocalContentColor.current.copy(alpha = 0.45f)
        Box(Modifier.width(36.dp + 16.dp + 16.dp))
        Text(key, style = TriTvType.Body, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("当前无可用源", style = TriTvType.Caption, color = dim)
    }
}

/** Frosted second bar: page name on the left, playlist stats and the guide and source buttons on the right. */
@Composable
private fun SubNav(state: UiState, onOpenSettings: () -> Unit, onOpenGuide: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(TriTvColors.Ink.copy(alpha = 0.8f))
            .drawBehind {
                drawLine(TriTvColors.Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = ScreenMargin),
    ) {
        Text(if (state.favoritesSelected) "收藏" else "频道", style = TriTvType.Tagline)
        if (state.movingFavorite != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(24.dp)
                    .background(TriTvColors.Focus, Radius.Pill)
                    .padding(horizontal = 10.dp),
            ) {
                Text("移动中", style = TriTvType.Small.copy(fontWeight = FontWeight.SemiBold))
            }
        }
        Spacer(Modifier.weight(1f))
        when {
            state.movingFavorite != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KeyHint("▲▼", "移动位置")
                KeyHint("OK", "放下")
                KeyHint("返回", "取消")
            }
            state.loading -> Text("加载中…", style = TriTvType.Caption, color = TriTvColors.TextDim)
            state.error != null -> Text(state.error, style = TriTvType.Caption, color = TriTvColors.Error, maxLines = 1)
            state.channels.isNotEmpty() -> Text(
                "${state.channels.size} 个频道 · ${state.groups.size} 个分组",
                style = TriTvType.Caption,
                color = TriTvColors.TextDim,
            )
        }
        if (state.programmes.isNotEmpty()) {
            UtilityButton(onClick = onOpenGuide) {
                GuideIcon()
                Text("节目表", style = TriTvType.Caption)
            }
        }
        UtilityButton(onClick = onOpenSettings) {
            RssIcon()
            Text("直播源", style = TriTvType.Caption)
        }
    }
}

/** The focused channel, centred in large type with its number, group, line count and current programme beneath. */
@Composable
private fun Hero(state: UiState, focusedIndex: Int, now: Long) {
    val index = focusedIndex.takeIf { it in state.channels.indices } ?: 0
    val channel = state.channels[index]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .background(TriTvColors.Black)
            .padding(horizontal = ScreenMargin),
    ) {
        Text(
            channel.name,
            style = TriTvType.Display,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("%03d".format(index + 1), style = TriTvType.Body.tabular(), color = TriTvColors.TextMuted)
            channel.group?.let {
                Dot()
                GroupCaption(it, TriTvType.Body, TriTvColors.TextMuted)
            }
            if (channel.sources.size > 1) {
                Dot()
                Text("${channel.sources.size} 条线路", style = TriTvType.Body, color = TriTvColors.TextMuted)
            }
        }
        val current = state.programmesOf(channel).nowAndNext(now).first
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("正在播", style = TriTvType.LabelStrong, color = TriTvColors.SkyBlue)
                Text(current.title, style = TriTvType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.dp))
                ProgressBar(current.progress(now), Modifier.width(96.dp))
                Text(timeRange(current), style = TriTvType.Caption.tabular(), color = TriTvColors.TextMuted)
            }
        }
    }
}

/**
 * 48dp row: number in a fixed 36dp right-aligned column, a 16dp star (or move arrows) slot, name,
 * current programme, then group and line count. 16dp gaps.
 */
@Composable
private fun ChannelRow(
    state: UiState,
    index: Int,
    number: Int,
    now: Long,
    moving: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channel = state.channels[index]
    FocusRow(
        onClick = onClick,
        onLongClick = onLongClick,
        selected = moving,
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        val dim = LocalContentColor.current.copy(alpha = 0.6f)
        Text(
            "%03d".format(number),
            style = TriTvType.Label.tabular(),
            color = dim,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            when {
                moving -> MoveIcon()
                state.isFavorite(channel) -> StarIcon()
            }
        }
        val current = state.programmesOf(channel).nowAndNext(now).first
        Text(
            channel.name,
            style = TriTvType.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (current != null) Modifier.width(180.dp) else Modifier.weight(1f),
        )
        if (current != null) {
            Text(
                current.title,
                style = TriTvType.Caption,
                color = dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (!state.favoritesSelected && state.selectedGroup == null && channel.group != null) {
            GroupCaption(channel.group, TriTvType.Caption, dim)
        }
        if (channel.sources.size > 1) {
            Text("${channel.sources.size} 线", style = TriTvType.Caption, color = dim)
        }
    }
}

/** 40dp pill for a group: emoji in a 20dp slot, 10dp gap, name, count on the right. */
@Composable
private fun GroupChip(label: GroupLabel, count: Int, selected: Boolean, onClick: () -> Unit) {
    FocusRow(
        onClick = onClick,
        selected = selected,
        shape = Radius.Pill,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        gap = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        IconSlot(label.icon, 20.dp, 16.sp)
        Text(
            label.name,
            style = TriTvType.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$count",
            style = TriTvType.Small.copy(fontSize = 13.sp, lineHeight = 20.sp).tabular(),
            color = LocalContentColor.current.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun EmptyHint(loading: Boolean, onOpenSettings: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(TriTvColors.Black),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (loading) "正在加载频道…" else "还没有频道", style = TriTvType.Display)
            if (!loading) PrimaryPill("设置直播源", onClick = onOpenSettings)
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

/** "16:30–18:45" for a programme, in the device's time zone. */
fun timeRange(p: Programme): String {
    val f = SimpleDateFormat("HH:mm", Locale.getDefault())
    return "${f.format(p.start)}–${f.format(p.stop)}"
}
