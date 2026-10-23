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
    val loading: Boolean = false,
    val error: String? = null,
    /** Index into [channels] of the channel being played, or null when browsing. */
    val playingIndex: Int? = null,
    /** Index the channel list should focus, e.g. after leaving the player. */
    val focusIndex: Int = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaylistRepository(app)

    private val _state = MutableStateFlow(UiState(playlistUrl = repo.savedUrl.orEmpty()))
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
                    val last = channels.indexOfFirst { it.url == repo.lastChannelUrl }.coerceAtLeast(0)
                    _state.update {
                        it.copy(
                            loading = false,
                            channels = channels,
                            focusIndex = last,
                            error = if (channels.isEmpty()) "播放列表中没有频道" else null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = "加载失败：${e.message ?: e.javaClass.simpleName}") }
                }
        }
    }

    fun play(index: Int) {
        val channels = _state.value.channels
        if (index !in channels.indices) return
        repo.lastChannelUrl = channels[index].url
        _state.update { it.copy(playingIndex = index, focusIndex = index) }
    }

    /** Moves to the next (+1) or previous (-1) channel, wrapping around the list. */
    fun zap(delta: Int) {
        val s = _state.value
        val current = s.playingIndex ?: return
        if (s.channels.isEmpty()) return
        play(Math.floorMod(current + delta, s.channels.size))
    }

    fun stop() = _state.update { it.copy(playingIndex = null) }
}
