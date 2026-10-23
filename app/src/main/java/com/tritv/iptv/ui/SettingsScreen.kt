package com.tritv.iptv.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.tritv.iptv.GuideStatus
import com.tritv.iptv.UiState
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The playlist source page: the M3U address and an optional guide (XMLTV) address on the left,
 * the result of the last playlist import and guide download on the right.
 */
@Composable
fun SettingsScreen(
    state: UiState,
    onUrlChange: (String) -> Unit,
    onGuideUrlChange: (String) -> Unit,
    onLoad: () -> Unit,
    onCancel: () -> Unit,
    onRefreshGuide: () -> Unit,
) {
    val canGoBack = state.channels.isNotEmpty()
    val fieldFocus = remember { FocusRequester() }
    val guideFocus = remember { FocusRequester() }
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
                .padding(top = 20.dp, bottom = 18.dp),
        ) {
            Text("直播源", style = TriTvType.Display)
            Text("播放列表必填；节目单可留空，留空时用播放列表里自带的地址。", style = TriTvType.Body, color = TriTvColors.TextMuted)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(TriTvColors.Ink)
                .padding(horizontal = ScreenMargin, vertical = 24.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(TriTvColors.Tile, Radius.Card)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("播放列表地址", style = TriTvType.LabelStrong)
                    UrlField(
                        value = state.playlistUrl,
                        onValueChange = onUrlChange,
                        onDone = onLoad,
                        next = guideFocus,
                        placeholder = "http://…/playlist.m3u",
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("节目单地址（XMLTV）", style = TriTvType.LabelStrong)
                        Text("可选", style = TriTvType.Caption, color = TriTvColors.TextDim)
                    }
                    UrlField(
                        value = state.guideUrl,
                        onValueChange = onGuideUrlChange,
                        onDone = onLoad,
                        next = buttonFocus,
                        placeholder = "留空则使用播放列表自带的节目单",
                        icon = { GuideIcon(color = TriTvColors.TextDim) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(guideFocus),
                    )
                }
                Spacer(Modifier.weight(1f))
                state.error?.let { Text(it, style = TriTvType.Caption, color = TriTvColors.Error, maxLines = 2) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Fixed width so the label can swap to the spinner and back without the row moving.
                    PrimaryPill(
                        text = "保存并导入",
                        onClick = onLoad,
                        enabled = state.loading || state.imported || state.playlistUrl.isNotBlank(),
                        busyText = if (state.loading) "导入中…" else null,
                        doneText = if (state.imported) "已导入" else null,
                        modifier = Modifier
                            .width(148.dp)
                            .focusRequester(buttonFocus),
                    )
                    if (canGoBack) SecondaryPill("取消", onClick = onCancel)
                    Spacer(Modifier.weight(1f))
                    Text("遥控器 OK 键弹出输入法", style = TriTvType.Small, color = TriTvColors.TextDim)
                }
            }

            if (state.loadedAt != null && state.channels.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight(),
                ) {
                    LastImportCard(state, state.loadedAt)
                    state.guideStatus?.let { GuideCard(it, onRefreshGuide, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A status card: a check (or warning) slot, a title, then caption lines indented under the title. */
@Composable
private fun StatusCard(
    title: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
    /** Shows a spinner in the check slot until the work finishes. */
    loading: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(TriTvColors.Tile, Radius.Card)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (loading) Spinner(size = 16.dp, stroke = 2.dp) else if (ok) CheckIcon() else Text("!", style = TriTvType.LabelStrong, color = TriTvColors.Error)
            }
            Text(title, style = TriTvType.Label.copy(fontWeight = FontWeight.SemiBold))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 28.dp)) {
            content()
        }
    }
}

@Composable
private fun GuideCard(status: GuideStatus, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    StatusCard("节目单", ok = status.error == null && status.matched > 0, loading = status.loading, modifier = modifier) {
        val dim = TriTvColors.TextMuted
        when {
            status.loading -> Text("正在下载并解析…", style = TriTvType.Caption, color = dim)
            status.error != null -> Text(status.error, style = TriTvType.Caption, color = dim, maxLines = 3)
            else -> {
                status.downloadedAt?.let {
                    Text(dayTime(it) + " 更新 · 每 12 小时自动刷新", style = TriTvType.Caption, color = dim)
                }
                Text(
                    "已匹配 ${status.matched} / ${status.total} 个频道",
                    style = TriTvType.Caption.tabular(),
                    color = dim,
                )
                ProgressBar(
                    if (status.total == 0) 0f else status.matched.toFloat() / status.total,
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                if (status.matched == 0) {
                    Text("节目单里的频道名和播放列表对不上", style = TriTvType.Caption, color = TriTvColors.Error)
                }
            }
        }
        status.url?.let {
            Text(
                (if (status.fromPlaylist) "来自播放列表：" else "") + it,
                style = TriTvType.Small,
                color = TriTvColors.TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!status.loading) {
            Spacer(Modifier.height(4.dp))
            UtilityButton(onClick = onRefresh) { Text("立即刷新节目单", style = TriTvType.Caption) }
        }
    }
}

private fun dayTime(t: Long): String {
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val prefix = if (today.format(t) == today.format(System.currentTimeMillis())) "今天" else SimpleDateFormat("M月d日", Locale.CHINA).format(t)
    return prefix + " " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(t)
}

@Composable
private fun LastImportCard(state: UiState, loadedAt: Long) {
    val lines = state.channels.sumOf { it.sources.size }
    StatusCard("播放列表", ok = true) {
        Text(dayTime(loadedAt) + " 导入", style = TriTvType.Caption, color = TriTvColors.TextMuted)
        Text(
            "${state.channels.size} 个频道 · ${state.groups.size} 个分组 · $lines 条线路",
            style = TriTvType.Caption.tabular(),
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
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = { LinkIcon(color = TriTvColors.TextDim) },
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
                icon()
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = TriTvType.Body, color = TriTvColors.TextDim, maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}
