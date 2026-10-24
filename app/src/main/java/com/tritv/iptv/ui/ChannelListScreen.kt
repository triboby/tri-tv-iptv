package com.tritv.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.tritv.iptv.UiState
import com.tritv.iptv.data.Channel
import com.tritv.iptv.epg.Programme
import com.tritv.iptv.epg.nowAndNext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Locale

/** The group rail while focus is in the channel list: one 40dp circle per group, icon only. */
private val RailCollapsed = 40.dp

/** The group rail while it has focus: icon, name and count. */
private val RailExpanded = 184.dp

private val DrawerListWidth = 288.dp
private val DrawerPadding = 24.dp
private val DrawerGap = 12.dp

/** Width of the drawer with its group rail collapsed, about 40% of the screen; the picture fills the rest. */
val DrawerWidth = DrawerPadding * 2 + RailCollapsed + DrawerGap + DrawerListWidth

/** Dark enough to read white text on any picture, light enough that the picture still shows through. */
private val DrawerScrim = Color.Black.copy(alpha = 0.82f)

/**
 * The channel drawer, on the left over the playing channel: a group rail that opens out when it has
 * focus, and the channel list. Resting on a channel plays it behind the drawer; OK plays it full screen.
 */
@Composable
fun ChannelListScreen(
    state: UiState,
    onPlay: (Int) -> Unit,
    /** Plays the channel behind the drawer, leaving the drawer open. */
    onPreview: (Int) -> Unit,
    /** Closes the drawer so the playing channel is full screen. */
    onClose: () -> Unit,
    onSelectGroup: (String?) -> Unit,
    onSelectFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onStartMove: (String) -> Unit,
    onMove: (Int) -> Unit,
    onFinishMove: (cancel: Boolean) -> Unit,
    /** Digits typed for a channel number; the player's corner card shows them. */
    numberEntry: NumberEntry,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    // Remembered: the getter builds a new list each time, and a new list recomposes every row.
    val visible = remember(state.channels, state.selectedGroup, state.favoritesSelected, state.favorites) { state.visibleIndices }
    val groupCounts = remember(state.channels) { state.channels.groupingBy { it.group }.eachCount() }
    val favoriteKeys = remember(state.favorites) { state.favorites.toHashSet() }
    // Read only in snapshots and in the header, never in this body: a focus move then recomposes
    // the header alone instead of the whole drawer.
    val focusedState = remember { mutableIntStateOf(state.focusIndex) }
    // Channel key whose long-press menu is open.
    var menuFor by remember { mutableStateOf<String?>(null) }
    val now = rememberNow()
    val moving = state.movingFavorite


    // Set while the loading skeleton shows and for a moment after the list replaces it, so the
    // first rows arrive one after another instead of popping in at once.
    var entrance by remember { mutableStateOf(false) }
    val empty = state.channels.isEmpty()
    LaunchedEffect(entrance, empty) {
        if (entrance && !empty) {
            delay(ENTRANCE_WINDOW_MS)
            entrance = false
        }
    }

    // Fetch every channel's logo in the background, so moving focus or zapping shows it at once.
    val context = LocalContext.current.applicationContext
    LaunchedEffect(state.channels) { LogoCache.preload(context, state.channels.map { it.logos }) }

    // Back closes the drawer onto the playing channel; while moving a favourite it cancels the move.
    BackHandler(enabled = moving == null && state.playingIndex != null) { onClose() }
    BackHandler(enabled = moving != null) { onFinishMove(true) }
    // Last, so it goes first: Back drops a half-typed number before anything else.
    BackHandler(enabled = numberEntry.active) { numberEntry.clear() }

    // Whether focus is in the group rail, which then opens out; resting on a group shows it without leaving the rail.
    var groupsFocused by remember { mutableStateOf(false) }
    // Bumped by OK on a group, to move focus into the list even though it is in the group rail.
    var focusListTick by remember { mutableIntStateOf(0) }
    val handledFocusListTick = remember { intArrayOf(0) }
    // Group chip with focus, shown once focus rests on it for GROUP_FOCUS_DELAY_MS.
    var focusedGroup by remember { mutableStateOf<GroupChoice?>(null) }
    val selectedGroupRequester = remember { FocusRequester() }
    // Ends of the rail, for moving between the groups and the entries at its foot and for wrapping round.
    val firstGroupRequester = remember { FocusRequester() }
    val railActionsRequester = remember { FocusRequester() }
    val lastActionRequester = remember { FocusRequester() }

    LaunchedEffect(focusedGroup) {
        val choice = focusedGroup ?: return@LaunchedEffect
        delay(GROUP_FOCUS_DELAY_MS)
        if (choice.favorites) onSelectFavorites() else onSelectGroup(choice.group)
    }

    // Resting on a channel plays it behind the drawer; moving through the list starts nothing.
    // Keyed on the channels too: the first list arrives after this has started, while focus still
    // reads 0 from before it, which would otherwise play channel 1 a second later.
    LaunchedEffect(groupsFocused, moving, state.channels) {
        if (groupsFocused || moving != null || state.channels.isEmpty()) return@LaunchedEffect
        snapshotFlow { focusedState.intValue }.collectLatest { index ->
            delay(PREVIEW_DELAY_MS)
            onPreview(index)
        }
    }

    // Put focus back on the playing channel when the drawer (re)opens.
    LaunchedEffect(state.channels, state.focusIndex, state.selectedGroup, state.favoritesSelected, focusListTick) {
        // Marked handled only once acted on, as a group's new state can restart this before then.
        val askedForList = focusListTick != handledFocusListTick[0]
        val pos = visible.indexOf(state.focusIndex)
        if (pos < 0) {
            handledFocusListTick[0] = focusListTick
            return@LaunchedEffect
        }
        listState.scrollToItem((pos - 3).coerceAtLeast(0))
        if (groupsFocused && !askedForList) return@LaunchedEffect
        handledFocusListTick[0] = focusListTick
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
                // ≡ (on every BRAVIA remote) or Guide opens the guide grid; consumed so the TV does
                // not show its own message. Favourites are in the long-press OK menu.
                if ((event.key == Key.Menu && moving == null) || event.key == Key.Guide) {
                    if (event.type == KeyEventType.KeyDown) onOpenGuide()
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
        if (state.channels.isEmpty()) {
            Box(Modifier.fillMaxSize().background(TriTvColors.Black)) {
                if (state.loading) {
                    LaunchedEffect(Unit) { entrance = true }
                    DrawerSkeleton()
                } else {
                    EmptyHint(onOpenSettings = onOpenSettings)
                }
            }
        } else {
            // Snaps rather than animates: while the list slides sideways, Down presses find no row to go to.
            val railWidth = if (groupsFocused) RailExpanded else RailCollapsed
            Row(
                horizontalArrangement = Arrangement.spacedBy(DrawerGap),
                modifier = Modifier
                    .fillMaxHeight()
                    .background(DrawerScrim)
                    .padding(DrawerPadding),
            ) {
                // Group rail. OK on a group shows it and moves into the list; resting on one shows it in place.
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .width(railWidth)
                        .fillMaxHeight()
                        // Only notes where focus is. Moving between the groups and the entries below them can
                        // report "no focus" for a moment, so sending focus anywhere from here bounced it back
                        // to the selected group; Left from the list does that instead.
                        .onFocusChanged { groupsFocused = it.hasFocus },
                ) {
                    val groupChip: @Composable (GroupChoice, GroupLabel, Int, Int, Boolean, Boolean) -> Unit = { choice, label, count, order, first, last ->
                        val selected = if (choice.favorites) state.favoritesSelected
                        else !state.favoritesSelected && state.selectedGroup == choice.group
                        GroupChip(
                            label = label,
                            // "全部" has no emoji; three drawn lines sit exactly in the middle, where a ☰ glyph would not.
                            drawnIcon = if (!choice.favorites && choice.group == null) ({ ListIcon() }) else null,
                            count = count,
                            selected = selected,
                            expanded = groupsFocused,
                            onClick = {
                                focusedGroup = null
                                if (choice.favorites) onSelectFavorites() else onSelectGroup(choice.group)
                                focusListTick++
                            },
                            modifier = Modifier
                                .arrive(order, entrance)
                                .onFocusChanged { if (it.isFocused) focusedGroup = choice else if (focusedGroup == choice) focusedGroup = null }
                                .then(if (selected) Modifier.focusRequester(selectedGroupRequester) else Modifier)
                                .then(if (first) Modifier.focusRequester(firstGroupRequester).jumpFocusOn(Key.DirectionUp, lastActionRequester) else Modifier)
                                .then(if (last) Modifier.jumpFocusOn(Key.DirectionDown, railActionsRequester) else Modifier),
                        )
                    }
                    // A plain scrolling column rather than a lazy one: there are few groups, every chip stays
                    // composed so the ends can always take focus, and Down leaves it like any other column.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        val hasFavorites = state.favorites.isNotEmpty()
                        if (hasFavorites) {
                            groupChip(GroupChoice(favorites = true), FAVORITES_LABEL, state.favorites.size, 1, true, false)
                        }
                        groupChip(GroupChoice(group = null), ALL_LABEL, state.channels.size, 2, !hasFavorites, state.groups.isEmpty())
                        state.groups.forEachIndexed { i, group ->
                            key(group) {
                                groupChip(
                                    GroupChoice(group = group),
                                    remember(group) { groupLabel(group) },
                                    groupCounts[group] ?: 0,
                                    3 + i,
                                    false,
                                    i == state.groups.lastIndex,
                                )
                            }
                        }
                    }
                    val hasGuide = state.programmes.isNotEmpty()
                    if (hasGuide) {
                        RailAction(
                            "节目表",
                            expanded = groupsFocused,
                            onClick = onOpenGuide,
                            key = "≡",
                            modifier = Modifier.focusRequester(railActionsRequester),
                        ) { GuideIcon() }
                    }
                    RailAction(
                        "直播源",
                        expanded = groupsFocused,
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .then(if (hasGuide) Modifier else Modifier.focusRequester(railActionsRequester))
                            .focusRequester(lastActionRequester)
                            .jumpFocusOn(Key.DirectionDown, firstGroupRequester),
                    ) { RssIcon() }
                }

                Column(
                    modifier = Modifier
                        .width(DrawerListWidth)
                        .fillMaxHeight(),
                ) {
                    // Read inside the header, so a focus move recomposes it alone and not the whole drawer.
                    ListHeader(state, { visible.indexOf(focusedState.intValue) }, visible.size)
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // Left lands on the group being shown, not whichever is level with the row.
                            // While a favourite is being moved, Up/Down move it and OK puts it down.
                            .onPreviewKeyEvent { event ->
                                if (moving == null) {
                                    if (event.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                                    if (event.type == KeyEventType.KeyDown) {
                                        runCatching { selectedGroupRequester.requestFocus() }
                                            .onFailure { runCatching { firstGroupRequester.requestFocus() } }
                                    }
                                    return@onPreviewKeyEvent true
                                }
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
                        itemsIndexed(visible, key = { _, index -> index }) { pos, index ->
                            val channel = state.channels[index]
                            ChannelRow(
                                channel = channel,
                                programmes = state.programmesOf(channel),
                                isFavorite = channel.stableKey in favoriteKeys,
                                number = index + 1,
                                now = now,
                                moving = channel.stableKey == moving,
                                playing = index == state.playingIndex,
                                onClick = { onPlay(index) },
                                onLongClick = { menuFor = channel.stableKey },
                                modifier = Modifier
                                    .arrive(pos + 1, entrance)
                                    .onFocusChanged { if (it.isFocused) focusedState.intValue = index }
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

/** How long focus rests on a channel before it plays behind the drawer, so moving through the list stays quick. */
private const val PREVIEW_DELAY_MS = 1_000L

/** A chip in the group rail: the favourites, or [group] (null for all channels). */
private data class GroupChoice(val group: String? = null, val favorites: Boolean = false)

/** How long after the skeleton gives way that newly shown rows still play their entrance. */
private const val ENTRANCE_WINDOW_MS = 1_000L

/** Rows past this many are off screen by the time they would arrive, so they just appear. */
private const val ENTRANCE_MAX_ORDER = 12

/**
 * Entrance after the loading skeleton: fade in and rise 8dp over 300ms, each [order] 30ms after
 * the one before. Without [play] (or past the first screenful) the element is simply there.
 */
@Composable
private fun Modifier.arrive(order: Int, play: Boolean): Modifier {
    val progress = remember { Animatable(if (play && order <= ENTRANCE_MAX_ORDER) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(100L + order * 30L)
            progress.animateTo(1f, tween(300, easing = EnterEasing))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 8.dp.toPx()
    }
}

/** [key] on this element moves focus straight to [target] instead of searching for the nearest element. */
private fun Modifier.jumpFocusOn(key: Key, target: FocusRequester): Modifier = onPreviewKeyEvent { event ->
    if (event.key != key) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyDown) runCatching { target.requestFocus() }
    true
}

/** How long focus rests on a group before the list shows it, so moving through groups stays quick. */
private const val GROUP_FOCUS_DELAY_MS = 300L

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
        contentPadding = PaddingValues(horizontal = 10.dp),
        gap = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        val dim = LocalContentColor.current.copy(alpha = 0.45f)
        Box(Modifier.width(NumberWidth))
        Text(key, style = TriTvType.Body, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("当前无可用源", style = TriTvType.Caption, color = dim)
    }
}

/**
 * Above the channel list: the group being shown, and where focus is in it. While a favourite is being
 * moved the keys for that go underneath; a reload or a load error replaces the position.
 */
@Composable
private fun ListHeader(state: UiState, focusedPos: () -> Int, count: Int) {
    val title = when {
        state.favoritesSelected -> "收藏"
        state.selectedGroup == null -> "全部"
        else -> remember(state.selectedGroup) { groupLabel(state.selectedGroup.orEmpty()).name }
    }
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                title,
                style = TriTvType.Tagline.copy(lineHeight = 24.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            when {
                state.movingFavorite != null -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(24.dp)
                        .background(TriTvColors.Focus, Radius.Pill)
                        .padding(horizontal = 10.dp),
                ) {
                    Text("移动中", style = TriTvType.Small.copy(fontWeight = FontWeight.SemiBold))
                }
                state.loading -> LoadingStatus()
                state.error != null -> Text(
                    state.error,
                    style = TriTvType.Caption,
                    color = TriTvColors.Error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
                else -> {
                    val pos = focusedPos()
                    Text(
                        if (pos >= 0) "${pos + 1} / $count" else "$count 个",
                        style = TriTvType.Caption.tabular(),
                        color = TriTvColors.TextMuted,
                    )
                }
            }
        }
        if (state.movingFavorite != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .height(28.dp)
                    .padding(horizontal = 10.dp),
            ) {
                KeyHint("▲▼", "移动")
                KeyHint("OK", "放下")
                KeyHint("返回", "取消")
            }
        }
    }
}

/** Channel numbers line up in this column, left-aligned with the header text above. */
private val NumberWidth = 28.dp

/**
 * 52dp row, 10dp in from both sides like the group chips, with 12dp gaps: number, then the name with its category and the
 * current programme under it, then "正在播" for the channel behind the drawer and a star for a favourite.
 */
@Composable
private fun ChannelRow(
    channel: Channel,
    programmes: List<Programme>,
    isFavorite: Boolean,
    number: Int,
    now: Long,
    moving: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    FocusRow(
        onClick = onClick,
        onLongClick = onLongClick,
        selected = moving,
        contentPadding = PaddingValues(horizontal = 10.dp),
        gap = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { focused = it.isFocused },
    ) {
        val dim = LocalContentColor.current.copy(alpha = 0.6f)
        Text(
            "%03d".format(number),
            style = TriTvType.Caption.tabular(),
            color = dim,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(NumberWidth),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    channel.name,
                    style = TriTvType.Body.copy(lineHeight = 22.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                channel.category?.let {
                    Text(it, style = TriTvType.Caption, color = dim, maxLines = 1, softWrap = false)
                }
            }
            val current = programmes.nowAndNext(now).first
            if (current != null) {
                Text(current.title, style = TriTvType.Small, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // White on the blue focus fill, the live blue otherwise.
        if (playing) NowPlayingMark(if (focused) TriTvColors.Text else TriTvColors.SkyBlue)
        when {
            moving -> MoveIcon()
            isFavorite -> StarIcon()
        }
    }
}

/** Three level bars and "正在播": the channel playing behind the drawer. */
@Composable
private fun NowPlayingMark(color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(14.dp)) {
            val unit = size.width / 14f
            val radius = CornerRadius(unit, unit)
            // x and height of each bar in a 14-unit box, standing on a common baseline.
            listOf(1f to 8f, 5.5f to 12f, 10f to 6f).forEach { (x, h) ->
                drawRoundRect(color, Offset(x * unit, (13f - h) * unit), Size(3f * unit, h * unit), radius)
            }
        }
        Text("正在播", style = TriTvType.Small, color = color, maxLines = 1, softWrap = false)
    }
}

/**
 * 40dp chip for a group, 10dp in from both sides, with the channel rows' 12dp corners. Collapsed it is a square with the group's emoji
 * (or the first character of its name); expanded the name and count follow after a 10dp gap.
 */
@Composable
private fun GroupChip(
    label: GroupLabel,
    count: Int,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn in the icon slot instead of the label's emoji. */
    drawnIcon: (@Composable () -> Unit)? = null,
) {
    // The group being shown is a soft grey fill, as in the design, rather than the usual blue ring;
    // focus still fills it blue over the grey.
    FocusRow(
        onClick = onClick,
        shape = Radius.Row,
        contentPadding = PaddingValues(horizontal = 10.dp),
        gap = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(if (selected) Modifier.background(SelectedGroupFill, Radius.Row) else Modifier),
    ) {
        if (drawnIcon != null) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { drawnIcon() }
        } else if (label.icon != null || expanded) {
            IconSlot(label.icon, 20.dp, 16.sp)
        } else {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Text(label.name.take(1), style = TriTvType.Label, maxLines = 1, softWrap = false)
            }
        }
        if (expanded) {
            Text(
                label.name,
                style = TriTvType.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$count",
                style = TriTvType.Small.copy(fontSize = 14.sp, lineHeight = 20.sp).tabular(),
                color = LocalContentColor.current.copy(alpha = 0.6f),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private val SelectedGroupFill = Color.White.copy(alpha = 0.16f)

/** Three 14dp lines, 4dp apart, centred in 16dp: the icon for all channels. */
@Composable
private fun ListIcon(color: Color = LocalContentColor.current) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 2.dp.toPx()
        val inset = 1.dp.toPx()
        listOf(4f, 8f, 12f).forEach { y ->
            val yPx = y.dp.toPx()
            drawLine(color, Offset(inset, yPx), Offset(size.width - inset, yPx), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** A chip at the foot of the group rail that opens another page; laid out like a [GroupChip]. */
@Composable
private fun RailAction(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    key: String? = null,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    FocusRow(
        onClick = onClick,
        shape = Radius.Row,
        contentPadding = PaddingValues(horizontal = 10.dp),
        gap = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        if (expanded) {
            Text(label, style = TriTvType.Label, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            if (key != null) KeyCap(key)
        }
    }
}

/**
 * Placeholder for the drawer while the first playlist loads, laid out like the real one so nothing
 * jumps when it arrives. Only the bars breathe; the surfaces stay put.
 */
@Composable
private fun DrawerSkeleton() {
    val pulse = rememberSkeletonAlpha()
    val bar = Color.White.copy(alpha = 0.08f * pulse)
    val faint = Color.White.copy(alpha = 0.06f * pulse)
    Row(
        horizontalArrangement = Arrangement.spacedBy(DrawerGap),
        modifier = Modifier
            .fillMaxHeight()
            .background(DrawerScrim)
            .padding(DrawerPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(RailCollapsed)) {
            repeat(5) { Box(Modifier.size(40.dp).background(faint, Radius.Row)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.width(DrawerListWidth)) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .height(46.dp)
                    .padding(start = 10.dp, bottom = 6.dp),
            ) {
                SkeletonBar(96.dp, 20.dp, bar, Radius.Small)
            }
            listOf(150, 120, 168, 136, 156, 110).forEach { w ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .padding(horizontal = 10.dp),
                ) {
                    Box(Modifier.width(NumberWidth)) { SkeletonBar(26.dp, 12.dp, bar) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBar(w.dp, 14.dp, bar)
                        SkeletonBar((w * 0.6f).dp, 10.dp, faint)
                    }
                }
            }
        }
    }
}

/** Small spinner and "正在加载频道…"; from 3 s on it says how long it has been waiting. */
@Composable
private fun LoadingStatus() {
    val started = remember { System.currentTimeMillis() }
    var waited by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            waited = ((System.currentTimeMillis() - started) / 1_000).toInt()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spinner(size = 14.dp, stroke = 1.5.dp, trackColor = Color(0x29FFFFFF))
        Text(
            if (waited >= 3) "仍在加载 · 已等待 $waited 秒" else "正在加载频道…",
            style = TriTvType.Caption.tabular(),
            color = TriTvColors.TextDim,
        )
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp, color: Color, shape: androidx.compose.ui.graphics.Shape = Radius.Pill) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(color, shape),
    )
}

@Composable
private fun EmptyHint(onOpenSettings: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(TriTvColors.Black),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("还没有频道", style = TriTvType.Display)
            PrimaryPill("设置直播源", onClick = onOpenSettings)
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

