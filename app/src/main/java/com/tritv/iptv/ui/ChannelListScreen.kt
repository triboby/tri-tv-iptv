package com.tritv.iptv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tritv.iptv.UiState

private val BandShape = RoundedCornerShape(24.dp)

@Composable
fun ChannelListScreen(
    state: UiState,
    onUrlChange: (String) -> Unit,
    onLoad: () -> Unit,
    onPlay: (Int) -> Unit,
    onSelectGroup: (String?) -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val visible = state.visibleIndices
    var focusedIndex by remember { mutableIntStateOf(state.focusIndex) }
    val focusedChannel = state.channels.getOrNull(focusedIndex)

    // Put focus back on the last played channel when the list (re)appears.
    LaunchedEffect(state.channels, state.focusIndex, state.selectedGroup) {
        val pos = visible.indexOf(state.focusIndex)
        if (pos < 0) return@LaunchedEffect
        listState.scrollToItem(pos)
        runCatching { focusRequester.requestFocus() }
    }

    AmbientBackground(glow = ambientColorFor(focusedChannel?.group ?: focusedChannel?.name)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 27.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                ClockHeader(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("TRI TV", style = EyebrowStyle)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UrlField(
                            value = state.playlistUrl,
                            onValueChange = onUrlChange,
                            onDone = onLoad,
                            modifier = Modifier.width(380.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        PillButton(onClick = onLoad, enabled = !state.loading) {
                            Text(if (state.loading) "加载中…" else "导入")
                        }
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.channels.isEmpty()) {
                EmptyHint(loading = state.loading)
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            FocusedTitle(state, focusedIndex)
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                if (state.groups.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                        modifier = Modifier.width(220.dp),
                    ) {
                        item(key = "__all__") {
                            GroupItem("全部", state.channels.size, state.selectedGroup == null) { onSelectGroup(null) }
                        }
                        items(state.groups, key = { it }) { group ->
                            GroupItem(
                                name = group,
                                count = state.channels.count { it.group == group },
                                selected = state.selectedGroup == group,
                            ) { onSelectGroup(group) }
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                }

                // The channel list sits on a dark band, like a dock.
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0x99101114), BandShape)
                        .border(1.dp, TriTvColors.GlassBottom, BandShape),
                ) {
                    items(visible, key = { it }) { index ->
                        ChannelItem(
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
}

/** The focused channel's name in large type above the list. */
@Composable
private fun FocusedTitle(state: UiState, focusedIndex: Int) {
    val channel = state.channels.getOrNull(focusedIndex) ?: return
    AnimatedContent(
        targetState = focusedIndex,
        transitionSpec = {
            (fadeIn() + slideInVertically { it / 6 }) togetherWith fadeOut()
        },
        label = "focusedTitle",
    ) { index ->
        val c = state.channels.getOrNull(index) ?: channel
        Column {
            Text(
                c.name,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                "%03d".format(index + 1),
                c.group,
                "${c.sources.size} 条线路".takeIf { c.sources.size > 1 },
            ).joinToString("   ·   ")
            Text(details, style = EyebrowStyle)
        }
    }
}

@Composable
private fun ChannelItem(state: UiState, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val channel = state.channels[index]
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Text(
                "%03d".format(index + 1),
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current.copy(alpha = 0.6f),
            )
        },
        trailingContent = {
            val details = listOfNotNull(
                channel.group.takeIf { state.selectedGroup == null },
                "${channel.sources.size} 线".takeIf { channel.sources.size > 1 },
            ).joinToString("  ·  ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
        },
        shape = TriTvListItem.shape(),
        colors = TriTvListItem.colors(),
        scale = TriTvListItem.scale(),
        glow = TriTvListItem.glow(),
        modifier = modifier,
    )
}

@Composable
private fun GroupItem(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.6f),
            )
        },
        shape = TriTvListItem.shape(RoundedCornerShape(14.dp)),
        colors = TriTvListItem.colors(),
        scale = TriTvListItem.scale(),
        glow = TriTvListItem.glow(),
    )
}

@Composable
private fun EmptyHint(loading: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (loading) "正在加载频道…" else "还没有频道",
                style = MaterialTheme.typography.headlineMedium,
            )
            if (!loading) {
                Spacer(Modifier.height(8.dp))
                Text("在右上角输入 M3U 播放列表地址，然后选择「导入」", style = MaterialTheme.typography.bodyMedium, color = TriTvColors.TextMid)
            }
        }
    }
}

@Composable
private fun UrlField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TriTvColors.Text),
        cursorBrush = SolidColor(TriTvColors.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .height(42.dp)
            .then(
                if (focused) {
                    Modifier
                        .background(Color(0x33FFFFFF), PillShape)
                        .border(2.dp, TriTvColors.Focus, PillShape)
                } else {
                    Modifier.glass()
                },
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    "M3U 地址，例如 http://…/playlist.m3u",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TriTvColors.TextDim,
                    maxLines = 1,
                )
            }
            inner()
        },
    )
}
