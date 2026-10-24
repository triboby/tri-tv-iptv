package com.tritv.iptv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tritv.iptv.data.Channel
import com.tritv.iptv.data.FavoriteList
import com.tritv.iptv.data.LineRepository
import com.tritv.iptv.data.LineStat
import com.tritv.iptv.data.PlaylistEntry
import com.tritv.iptv.data.PlaylistRepository
import com.tritv.iptv.data.guideErrorMessage
import com.tritv.iptv.data.loadErrorMessage
import com.tritv.iptv.epg.GuideRepository
import com.tritv.iptv.epg.Programme
import com.tritv.iptv.epg.resolveGuideUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the programme guide for the source page's status card. */
data class GuideStatus(
    val loading: Boolean = false,
    /** When the guide file in use was downloaded. */
    val downloadedAt: Long? = null,
    /** Channels with programmes, out of [total]. */
    val matched: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    /** True when the address came from the playlist header rather than the viewer. */
    val fromPlaylist: Boolean = false,
    /** The address actually fetched, after moving a loopback host to the playlist's host. */
    val url: String? = null,
)

data class UiState(
    val playlistUrl: String = "",
    /** Guide address the viewer typed; empty to use the playlist's own. */
    val guideUrl: String = "",
    val channels: List<Channel> = emptyList(),
    /** Distinct group titles in playlist order. */
    val groups: List<String> = emptyList(),
    /** Group shown in the list, or null for all channels. Ignored while [favoritesSelected]. */
    val selectedGroup: String? = null,
    /** Whether the list shows the favourites instead of a group. */
    val favoritesSelected: Boolean = false,
    /** Favourite [Channel.stableKey]s in the viewer's order. */
    val favorites: List<String> = emptyList(),
    /** Favourite being moved with Up/Down, or null. */
    val movingFavorite: String? = null,
    val loading: Boolean = false,
    /** Set for a moment after an import from the source page succeeds, before going back to the list. */
    val imported: Boolean = false,
    val error: String? = null,
    /** Index into [channels] of the channel playing, full screen or behind the channel drawer. */
    val playingIndex: Int? = null,
    /** Whether the channel drawer is open over the playing channel; closed means full screen. */
    val listOpen: Boolean = true,
    /** The playing channel's lines in the order to try them, fixed when it started. */
    val playingLines: List<PlaylistEntry> = emptyList(),
    /** Index into [channels] the list should focus, e.g. after leaving the player. */
    val focusIndex: Int = 0,
    /** Whether the playlist source page is shown instead of the channel list. */
    val showSettings: Boolean = false,
    /** Whether the programme guide grid is shown. */
    val showGuide: Boolean = false,
    /** When the current channel list was last loaded, for the source page's status card. */
    val loadedAt: Long? = null,
    /** Guide programmes per [Channel.stableKey], sorted by start time. */
    val programmes: Map<String, List<Programme>> = emptyMap(),
    val guideStatus: GuideStatus? = null,
) {
    /** Indices into [channels] shown in the list; channel switching stays within them. */
    val visibleIndices: List<Int>
        get() = when {
            favoritesSelected -> favorites.mapNotNull { key ->
                channels.indexOfFirst { it.stableKey == key }.takeIf { it >= 0 }
            }
            selectedGroup == null -> channels.indices.toList()
            else -> channels.indices.filter { channels[it].group == selectedGroup }
        }

    /** Favourites whose channel is no longer in the playlist, kept so they come back when it returns. */
    val missingFavorites: List<String>
        get() = favorites.filter { key -> channels.none { it.stableKey == key } }

    fun isFavorite(channel: Channel) = channel.stableKey in favorites

    fun programmesOf(channel: Channel): List<Programme> = programmes[channel.stableKey].orEmpty()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaylistRepository(app)
    private val guideRepo = GuideRepository(app)
    private val lines = LineRepository(app)

    /** Probe results and playback outcomes per stream URL. */
    val lineStats: StateFlow<Map<String, LineStat>> = lines.stats

    /** Guide address from the last loaded playlist's header. */
    private var playlistGuideUrl: String? = null
    private var guideJob: Job? = null
    private var sweepJob: Job? = null

    /** Favourites as they were when moving started, restored if the move is cancelled. */
    private var favoritesBeforeMove: List<String>? = null

    private val _state = MutableStateFlow(
        UiState(
            playlistUrl = repo.savedUrl.orEmpty(),
            guideUrl = repo.guideUrl.orEmpty(),
            favorites = repo.favorites,
            showSettings = repo.savedUrl.isNullOrBlank(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (_state.value.playlistUrl.isNotBlank()) loadPlaylist(_state.value.playlistUrl)
    }

    fun onUrlChange(url: String) = _state.update { it.copy(playlistUrl = url) }

    fun onGuideUrlChange(url: String) = _state.update { it.copy(guideUrl = url) }

    fun loadPlaylist(url: String = _state.value.playlistUrl) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val guideChanged = _state.value.guideUrl.trim() != repo.guideUrl.orEmpty()
        repo.guideUrl = _state.value.guideUrl.trim().ifEmpty { null }
        _state.update { it.copy(loading = true, imported = false, error = null) }
        viewModelScope.launch {
            runCatching { repo.load(trimmed) }
                .onSuccess { playlist ->
                    val channels = playlist.channels
                    repo.savedUrl = trimmed
                    playlistGuideUrl = playlist.guideUrl
                    val last = channels.indexOfFirst { it.stableKey == repo.lastChannelKey }.coerceAtLeast(0)
                    // The last channel plays behind the drawer from the start.
                    val playing = last.takeIf { channels.isNotEmpty() }
                    val fromSourcePage = _state.value.showSettings && channels.isNotEmpty()
                    _state.update {
                        it.copy(
                            loading = false,
                            channels = channels,
                            groups = channels.mapNotNull { c -> c.group }.distinct(),
                            selectedGroup = null,
                            favoritesSelected = false,
                            movingFavorite = null,
                            focusIndex = last,
                            playingIndex = playing,
                            playingLines = playing?.let { i -> lines.ordered(channels[i]) }.orEmpty(),
                            listOpen = true,
                            error = if (channels.isEmpty()) "播放列表中没有频道" else null,
                            loadedAt = System.currentTimeMillis(),
                            // Back to the list once there is something to show, after "已导入" has been seen.
                            showSettings = it.showSettings,
                            imported = fromSourcePage,
                        )
                    }
                    if (fromSourcePage) {
                        viewModelScope.launch {
                            delay(IMPORTED_NOTE_MS)
                            _state.update { if (it.imported) it.copy(showSettings = false, imported = false) else it }
                        }
                    }
                    refreshGuide(force = guideChanged)
                    lines.prune(channels)
                    startLineSweep()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = loadErrorMessage(e),
                            // Nothing to browse, so show the source page where the address can be fixed.
                            showSettings = it.showSettings || it.channels.isEmpty(),
                        )
                    }
                }
        }
    }

    /**
     * Loads the guide for the current channels (from cache when it is recent enough), then
     * keeps it fresh: it is fetched again every [GuideRepository.MAX_AGE_MS] while the app runs.
     */
    fun refreshGuide(force: Boolean = false) {
        val channels = _state.value.channels
        val own = repo.guideUrl
        val url = (own ?: playlistGuideUrl)?.let { resolveGuideUrl(it, repo.savedUrl.orEmpty()) }
        guideJob?.cancel()
        if (url == null || channels.isEmpty()) {
            _state.update {
                it.copy(
                    programmes = emptyMap(),
                    guideStatus = if (channels.isEmpty()) null else GuideStatus(error = "播放列表没有提供节目单地址，可以在上面填写"),
                )
            }
            return
        }
        _state.update {
            it.copy(guideStatus = (it.guideStatus ?: GuideStatus()).copy(loading = true, error = null, fromPlaylist = own == null, url = url))
        }
        guideJob = viewModelScope.launch {
            var forceNext = force
            while (true) {
                runCatching { guideRepo.load(url, channels, forceNext) }
                    .onSuccess { data ->
                        _state.update {
                            it.copy(
                                programmes = data.programmes,
                                guideStatus = GuideStatus(
                                    downloadedAt = data.downloadedAt,
                                    matched = channels.count { c -> c.stableKey in data.programmes },
                                    total = channels.size,
                                    fromPlaylist = own == null,
                                    url = url,
                                ),
                            )
                        }
                    }
                    .onFailure { e ->
                        _state.update {
                            it.copy(guideStatus = GuideStatus(error = guideErrorMessage(e), fromPlaylist = own == null, url = url))
                        }
                    }
                forceNext = true
                delay(GuideRepository.MAX_AGE_MS)
            }
        }
    }

    /**
     * Probes the lines of every channel that has backups in the background, and again every
     * [LineRepository.SWEEP_MAX_AGE_MS], so opening a channel tries its fastest working line
     * first. The last channel and the favourites go first.
     */
    private fun startLineSweep() {
        sweepJob?.cancel()
        sweepJob = viewModelScope.launch {
            while (true) {
                val s = _state.value
                val first = (listOfNotNull(repo.lastChannelKey) + s.favorites).toSet()
                val (likely, rest) = s.channels.partition { it.stableKey in first }
                lines.sweep(likely + rest, LineRepository.SWEEP_MAX_AGE_MS)
                delay(LineRepository.SWEEP_MAX_AGE_MS)
            }
        }
    }

    /** Records how playing [entry] of the playing channel went. */
    fun reportLine(entry: PlaylistEntry, ok: Boolean) = lines.report(entry, ok)

    /** Remembers [entry] as the line the viewer chose for the playing channel. */
    fun pickLine(entry: PlaylistEntry) {
        val s = _state.value
        val channel = s.playingIndex?.let { s.channels.getOrNull(it) } ?: return
        lines.pick(channel, entry)
    }

    fun openSettings() = _state.update { it.copy(showSettings = true, showGuide = false, error = null) }

    /** Leaves the source page; stays on it while there are no channels to go back to. */
    fun closeSettings(): Boolean {
        if (_state.value.channels.isEmpty()) return false
        _state.update {
            it.copy(
                showSettings = false,
                imported = false,
                playlistUrl = repo.savedUrl ?: it.playlistUrl,
                guideUrl = repo.guideUrl.orEmpty(),
                error = null,
            )
        }
        return true
    }

    fun openGuide() = _state.update { it.copy(showGuide = true) }

    fun closeGuide() = _state.update { it.copy(showGuide = false) }

    fun selectGroup(group: String?) = _state.update { s ->
        if (!s.favoritesSelected && s.selectedGroup == group) return@update s
        val next = s.copy(selectedGroup = group, favoritesSelected = false, movingFavorite = null)
        next.copy(focusIndex = next.visibleIndices.firstOrNull() ?: 0)
    }

    fun selectFavorites() = _state.update { s ->
        if (s.favoritesSelected) return@update s
        val next = s.copy(favoritesSelected = true, movingFavorite = null)
        next.copy(focusIndex = next.visibleIndices.firstOrNull() ?: 0)
    }

    fun toggleFavorite(key: String) {
        val list = FavoriteList.toggle(_state.value.favorites, key)
        repo.favorites = list
        _state.update { it.copy(favorites = list) }
    }

    fun startMovingFavorite(key: String) {
        if (key !in _state.value.favorites) return
        favoritesBeforeMove = _state.value.favorites
        _state.update { it.copy(movingFavorite = key) }
    }

    /** Moves the favourite being moved one place up (-1) or down (+1). */
    fun moveFavorite(delta: Int) {
        val key = _state.value.movingFavorite ?: return
        _state.update { it.copy(favorites = FavoriteList.move(it.favorites, key, delta)) }
    }

    /** Ends moving: keeps the new order, or with [cancel] puts the old one back. */
    fun finishMovingFavorite(cancel: Boolean = false) {
        val before = favoritesBeforeMove
        favoritesBeforeMove = null
        _state.update { it.copy(movingFavorite = null, favorites = if (cancel && before != null) before else it.favorites) }
        repo.favorites = _state.value.favorites
    }

    /** Plays channel [index] full screen; the one already playing behind the drawer just goes full screen. */
    fun play(index: Int) {
        if (index == _state.value.playingIndex) {
            _state.update { it.copy(listOpen = false, focusIndex = index, showGuide = false) }
            return
        }
        start(index, listOpen = false)
    }

    /** Switches the channel playing behind the open drawer, keeping the drawer open. */
    fun preview(index: Int) {
        if (index == _state.value.playingIndex) return
        start(index, listOpen = true)
    }

    private fun start(index: Int, listOpen: Boolean) {
        val channels = _state.value.channels
        if (index !in channels.indices) return
        val channel = channels[index]
        repo.lastChannelKey = channel.stableKey
        _state.update {
            it.copy(
                playingIndex = index,
                playingLines = lines.ordered(channel),
                focusIndex = if (listOpen) it.focusIndex else index,
                listOpen = listOpen,
                showGuide = false,
            )
        }
        // Fresh numbers let the player skip a line that turns out dead before its own timeout.
        if (channel.sources.size > 1) {
            viewModelScope.launch { lines.probeChannel(channel, LineRepository.PLAY_MAX_AGE_MS) }
        }
    }

    /** Moves to the next (+1) or previous (-1) channel in the current group, wrapping around. */
    fun zap(delta: Int) {
        zapTarget(delta)?.let(::play)
    }

    /** Index of the channel [delta] steps from the one playing within the current list, wrapping around. */
    fun zapTarget(delta: Int): Int? {
        val s = _state.value
        val current = s.playingIndex ?: return null
        val visible = s.visibleIndices
        if (visible.isEmpty()) return null
        val pos = visible.indexOf(current).takeIf { it >= 0 } ?: 0
        return visible[Math.floorMod(pos + delta, visible.size)]
    }

    /**
     * Plays the channel with list number [number] (1-based, as shown), if there is one. Numbers
     * count over all channels, so when the channel is outside the current list the list becomes
     * all channels and channel up/down carries on from there.
     */
    fun playNumber(number: Int) {
        val index = number - 1
        if (index !in _state.value.channels.indices) return
        _state.update { s ->
            if (index in s.visibleIndices) s
            else s.copy(selectedGroup = null, favoritesSelected = false, movingFavorite = null)
        }
        play(index)
    }

    /** Opens the channel drawer over the playing channel, with focus on it. */
    fun openList() = _state.update { it.copy(listOpen = true, focusIndex = it.playingIndex ?: it.focusIndex) }

    /** Closes the drawer so the playing channel is full screen; stays open when nothing plays. */
    fun closeList() = _state.update { if (it.playingIndex != null) it.copy(listOpen = false) else it }
}

/** How long "已导入" stays on the source page before the list comes back. */
private const val IMPORTED_NOTE_MS = 800L
