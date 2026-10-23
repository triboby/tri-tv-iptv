package com.tritv.iptv.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.tritv.iptv.UiState

@Composable
fun ChannelListScreen(
    state: UiState,
    onPlay: (Int) -> Unit,
    onSelectGroup: (String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val visible = state.visibleIndices
    var focusedIndex by remember { mutableIntStateOf(state.focusIndex) }

    // Put focus back on the last played channel when the list (re)appears.
    LaunchedEffect(state.channels, state.focusIndex, state.selectedGroup) {
        val pos = visible.indexOf(state.focusIndex)
        if (pos < 0) return@LaunchedEffect
        listState.scrollToItem(pos)
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TriTvColors.Black),
    ) {
        TopBar()
        SubNav(state, onOpenSettings)

        if (state.channels.isEmpty()) {
            EmptyHint(loading = state.loading, onOpenSettings = onOpenSettings)
            return@Column
        }

        Hero(state, focusedIndex)

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
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    modifier = Modifier
                        .width(184.dp)
                        .fillMaxHeight(),
                ) {
                    item(key = "__all__") {
                        GroupChip(null, state.channels.size, state.selectedGroup == null) { onSelectGroup(null) }
                    }
                    items(state.groups, key = { it }) { group ->
                        GroupChip(
                            group = group,
                            count = state.channels.count { it.group == group },
                            selected = state.selectedGroup == group,
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
                    .background(TriTvColors.Tile, Radius.Card),
            ) {
                items(visible, key = { it }) { index ->
                    ChannelRow(
                        state = state,
                        index = index,
                        onClick = { onPlay(index) },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) focusedIndex = index }
                            .then(if (index == state.focusIndex) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                }
            }
        }
    }
}

/** Frosted second bar: page name on the left, playlist stats and the source button on the right. */
@Composable
private fun SubNav(state: UiState, onOpenSettings: () -> Unit) {
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
        Text("频道", style = TriTvType.Tagline)
        Spacer(Modifier.weight(1f))
        when {
            state.loading -> Text("加载中…", style = TriTvType.Caption, color = TriTvColors.TextDim)
            state.error != null -> Text(state.error, style = TriTvType.Caption, color = TriTvColors.Error, maxLines = 1)
            state.channels.isNotEmpty() -> Text(
                "${state.channels.size} 个频道 · ${state.groups.size} 个分组",
                style = TriTvType.Caption,
                color = TriTvColors.TextDim,
            )
        }
        UtilityButton(onClick = onOpenSettings) {
            RssIcon()
            Text("直播源", style = TriTvType.Caption)
        }
    }
}

/** The focused channel, centred in large type with its number, group and line count beneath. */
@Composable
private fun Hero(state: UiState, focusedIndex: Int) {
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
            Text("%03d".format(index + 1), style = TriTvType.Body, color = TriTvColors.TextMuted)
            channel.group?.let {
                Dot()
                GroupCaption(it, TriTvType.Body, TriTvColors.TextMuted)
            }
            if (channel.sources.size > 1) {
                Dot()
                Text("${channel.sources.size} 条线路", style = TriTvType.Body, color = TriTvColors.TextMuted)
            }
        }
    }
}

/** 48dp row: number in a fixed 36dp right-aligned column, 16dp gaps, name, then group and line count. */
@Composable
private fun ChannelRow(state: UiState, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val channel = state.channels[index]
    FocusRow(onClick = onClick, modifier = modifier.fillMaxWidth().height(48.dp)) {
        val dim = LocalContentColor.current.copy(alpha = 0.6f)
        Text(
            "%03d".format(index + 1),
            style = TriTvType.Label,
            color = dim,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        Text(
            channel.name,
            style = TriTvType.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (state.selectedGroup == null && channel.group != null) {
            GroupCaption(channel.group, TriTvType.Caption, dim)
        }
        if (channel.sources.size > 1) {
            Text("${channel.sources.size} 线", style = TriTvType.Caption, color = dim)
        }
    }
}

/** 40dp pill for a group: emoji in a 20dp slot, 10dp gap, name, count on the right. */
@Composable
private fun GroupChip(group: String?, count: Int, selected: Boolean, onClick: () -> Unit) {
    val label = remember(group) { group?.let(::groupLabel) ?: GroupLabel(null, "全部") }
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
            style = TriTvType.Small.copy(fontSize = 13.sp, lineHeight = 20.sp),
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
