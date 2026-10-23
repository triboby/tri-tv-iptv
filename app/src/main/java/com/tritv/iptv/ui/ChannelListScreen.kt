package com.tritv.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tritv.iptv.UiState

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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tri TV", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(24.dp))
            UrlField(
                value = state.playlistUrl,
                onValueChange = onUrlChange,
                onDone = onLoad,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Button(onClick = onLoad, enabled = !state.loading) {
                Text(if (state.loading) "加载中…" else "导入")
            }
        }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxSize()) {
            if (state.groups.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
                Spacer(Modifier.width(24.dp))
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(visible, key = { it }) { index ->
                    val channel = state.channels[index]
                    val details = listOfNotNull(
                        channel.group.takeIf { state.selectedGroup == null },
                        "${channel.sources.size} 条线路".takeIf { channel.sources.size > 1 },
                    ).joinToString("  ·  ")
                    val supporting: (@Composable () -> Unit)? =
                        details.takeIf { it.isNotEmpty() }?.let { d -> @Composable { Text(d, maxLines = 1) } }
                    ListItem(
                        selected = false,
                        onClick = { onPlay(index) },
                        headlineContent = {
                            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { Text("%03d".format(index + 1)) },
                        supportingContent = supporting,
                        modifier = if (index == state.focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupItem(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Text("$count") },
    )
}

@Composable
private fun UrlField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(colors.surface, RoundedCornerShape(8.dp))
            .border(2.dp, if (focused) colors.primary else colors.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text("输入 M3U 播放列表地址，例如 http://…/playlist.m3u", color = colors.onSurfaceVariant)
            }
            inner()
        },
    )
}
