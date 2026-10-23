package com.tritv.iptv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tritv.iptv.data.Channel
import com.tritv.iptv.data.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val playlistUrl: String = "",
    val channels: List<Channel> = emptyList(),
    /** Distinct group titles in playlist order. */
    val groups: List<String> = emptyList(),
    /** Group shown in the list, or null for all channels. */
    val selectedGroup: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Index into [channels] of the channel being played, or null when browsing. */
    val playingIndex: Int? = null,
    /** Index into [channels] the list should focus, e.g. after leaving the player. */
    val focusIndex: Int = 0,
    /** Whether the playlist source page is shown instead of the channel list. */
    val showSettings: Boolean = false,
    /** When the current channel list was last loaded, for the source page's status card. */
    val loadedAt: Long? = null,
) {
    /** Indices into [channels] shown under [selectedGroup]; channel switching stays within them. */
    val visibleIndices: List<Int>
        get() = if (selectedGroup == null) channels.indices.toList()
        else channels.indices.filter { channels[it].group == selectedGroup }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaylistRepository(app)

    private val _state = MutableStateFlow(
        UiState(playlistUrl = repo.savedUrl.orEmpty(), showSettings = repo.savedUrl.isNullOrBlank()),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (_state.value.playlistUrl.isNotBlank()) loadPlaylist(_state.value.playlistUrl)
    }

    fun onUrlChange(url: String) = _state.update { it.copy(playlistUrl = url) }

    fun loadPlaylist(url: String = _state.value.playlistUrl) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.load(trimmed) }
                .onSuccess { channels ->
                    repo.savedUrl = trimmed
                    val last = channels.indexOfFirst { it.stableKey == repo.lastChannelKey }.coerceAtLeast(0)
                    _state.update {
                        it.copy(
                            loading = false,
                            channels = channels,
                            groups = channels.mapNotNull { c -> c.group }.distinct(),
                            selectedGroup = null,
                            focusIndex = last,
                            error = if (channels.isEmpty()) "播放列表中没有频道" else null,
                            loadedAt = System.currentTimeMillis(),
                            // Back to the list once there is something to show.
                            showSettings = it.showSettings && channels.isEmpty(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = "加载失败：${e.message ?: e.javaClass.simpleName}",
                            // Nothing to browse, so show the source page where the address can be fixed.
                            showSettings = it.showSettings || it.channels.isEmpty(),
                        )
                    }
                }
        }
    }

    fun openSettings() = _state.update { it.copy(showSettings = true, error = null) }

    /** Leaves the source page; stays on it while there are no channels to go back to. */
    fun closeSettings(): Boolean {
        if (_state.value.channels.isEmpty()) return false
        _state.update { it.copy(showSettings = false, playlistUrl = repo.savedUrl ?: it.playlistUrl, error = null) }
        return true
    }

    fun selectGroup(group: String?) = _state.update { s ->
        val next = s.copy(selectedGroup = group)
        next.copy(focusIndex = next.visibleIndices.firstOrNull() ?: 0)
    }

    fun play(index: Int) {
        val channels = _state.value.channels
        if (index !in channels.indices) return
        repo.lastChannelKey = channels[index].stableKey
        _state.update { it.copy(playingIndex = index, focusIndex = index) }
    }

    /** Moves to the next (+1) or previous (-1) channel in the current group, wrapping around. */
    fun zap(delta: Int) {
        val s = _state.value
        val current = s.playingIndex ?: return
        val visible = s.visibleIndices
        if (visible.isEmpty()) return
        val pos = visible.indexOf(current).takeIf { it >= 0 } ?: 0
        play(visible[Math.floorMod(pos + delta, visible.size)])
    }

    fun stop() = _state.update { it.copy(playingIndex = null) }
}
