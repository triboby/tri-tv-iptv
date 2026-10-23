package com.tritv.iptv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.tritv.iptv.UiState
import java.text.SimpleDateFormat
import java.util.Locale

/** The playlist source page: one M3U address, import, and the result of the last import. */
@Composable
fun SettingsScreen(
    state: UiState,
    onUrlChange: (String) -> Unit,
    onLoad: () -> Unit,
    onCancel: () -> Unit,
) {
    val canGoBack = state.channels.isNotEmpty()
    val fieldFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TriTvColors.Black),
    ) {
        TopBar {
            if (canGoBack) {
                KeyCap("返回")
                Text("频道列表", style = TriTvType.Small, color = TriTvColors.TextMuted)
                Spacer(Modifier.width(12.dp))
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 20.dp),
        ) {
            Text("直播源", style = TriTvType.Display)
            Text("填入 M3U 播放列表地址，支持 http 和 https。", style = TriTvType.Body, color = TriTvColors.TextMuted)
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(TriTvColors.Ink)
                .padding(horizontal = ScreenMargin, vertical = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.width(600.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TriTvColors.Tile, Radius.Card)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("播放列表地址", style = TriTvType.LabelStrong)
                        UrlField(
                            value = state.playlistUrl,
                            onValueChange = onUrlChange,
                            onDone = onLoad,
                            next = buttonFocus,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(fieldFocus),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryPill(
                            text = if (state.loading) "导入中…" else "导入并返回",
                            onClick = onLoad,
                            enabled = !state.loading && state.playlistUrl.isNotBlank(),
                            modifier = Modifier.focusRequester(buttonFocus),
                        )
                        if (canGoBack) SecondaryPill("取消", onClick = onCancel)
                        Spacer(Modifier.weight(1f))
                        Text("遥控器 OK 键弹出输入法", style = TriTvType.Small, color = TriTvColors.TextDim)
                    }
                    state.error?.let { Text(it, style = TriTvType.Caption, color = TriTvColors.Error) }
                }

                if (state.loadedAt != null && state.channels.isNotEmpty()) {
                    LastImportCard(state, state.loadedAt)
                }
            }
        }
    }
}

@Composable
private fun LastImportCard(state: UiState, loadedAt: Long) {
    val lines = state.channels.sumOf { it.sources.size }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(loadedAt)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(TriTvColors.Tile, Radius.Card)
            .padding(horizontal = 20.dp),
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(16.dp)) {
                val w = size.width
                val stroke = 2.5.dp.toPx()
                drawLine(TriTvColors.SkyBlue, Offset(w * 0.12f, w * 0.52f), Offset(w * 0.4f, w * 0.8f), stroke, StrokeCap.Round)
                drawLine(TriTvColors.SkyBlue, Offset(w * 0.4f, w * 0.8f), Offset(w * 0.9f, w * 0.25f), stroke, StrokeCap.Round)
            }
        }
        Text("上次导入成功", style = TriTvType.Label, modifier = Modifier.weight(1f))
        Text(
            "$time · ${state.channels.size} 个频道 · ${state.groups.size} 个分组 · $lines 条线路",
            style = TriTvType.Caption,
            color = TriTvColors.TextMuted,
        )
    }
}

/**
 * Address field for a remote: focusing it does not open the keyboard. OK starts editing and shows the
 * keyboard; Down (or Right when not editing) hides it and moves to [next]; the keyboard's Go imports.
 */
@Composable
private fun UrlField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    next: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) { if (editing) keyboard?.show() else keyboard?.hide() }

    fun leave() {
        editing = false
        keyboard?.hide()
        runCatching { next.requestFocus() }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        readOnly = !editing,
        textStyle = TriTvType.Body.copy(color = TriTvColors.Text),
        cursorBrush = SolidColor(if (editing) TriTvColors.Focus else Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = {
            editing = false
            onDone()
        }),
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) editing = false
            }
            .onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when {
                        editing -> false
                        // Consume both halves of the press; start editing on release.
                        !down -> {
                            editing = true
                            true
                        }
                        else -> true
                    }
                    Key.DirectionDown -> {
                        if (down) leave()
                        true
                    }
                    Key.DirectionRight -> if (editing) {
                        false
                    } else {
                        if (down) leave()
                        true
                    }
                    else -> false
                }
            }
            .height(44.dp)
            .background(TriTvColors.Ink, Radius.Pill)
            .border(if (focused) 2.dp else 1.dp, if (focused) TriTvColors.Focus else TriTvColors.Hairline, Radius.Pill)
            .padding(start = 16.dp, end = 20.dp),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxHeight(),
            ) {
                LinkIcon(color = TriTvColors.TextDim)
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("http://…/playlist.m3u", style = TriTvType.Body, color = TriTvColors.TextDim, maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}
