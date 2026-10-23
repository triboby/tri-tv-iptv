package com.tritv.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tritv.iptv.player.PlayerScreen
import com.tritv.iptv.ui.ChannelListScreen
import com.tritv.iptv.ui.GuideScreen
import com.tritv.iptv.ui.SettingsScreen
import com.tritv.iptv.ui.TriTvTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TriTvTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val playing = state.playingIndex
                if (playing != null && playing in state.channels.indices) {
                    val channel = state.channels[playing]
                    PlayerScreen(
                        channel = channel,
                        channelNumber = playing + 1,
                        programmes = state.programmesOf(channel),
                        isFavorite = state.isFavorite(channel),
                        onToggleFavorite = { viewModel.toggleFavorite(channel.stableKey) },
                        onOpenGuide = viewModel::openGuide,
                        zapTarget = viewModel::zapTarget,
                        channelName = { n -> state.channels.getOrNull(n - 1)?.name },
                        onZap = viewModel::zap,
                        onPlayNumber = viewModel::playNumber,
                        onExit = viewModel::stop,
                    )
                } else if (state.showGuide && state.channels.isNotEmpty()) {
                    GuideScreen(state = state, onPlay = viewModel::play, onBack = viewModel::closeGuide)
                } else if (state.showSettings) {
                    BackHandler(enabled = state.channels.isNotEmpty()) { viewModel.closeSettings() }
                    SettingsScreen(
                        state = state,
                        onUrlChange = viewModel::onUrlChange,
                        onGuideUrlChange = viewModel::onGuideUrlChange,
                        onLoad = { viewModel.loadPlaylist() },
                        onCancel = { viewModel.closeSettings() },
                        onRefreshGuide = { viewModel.refreshGuide(force = true) },
                    )
                } else {
                    ChannelListScreen(
                        state = state,
                        onPlay = viewModel::play,
                        onSelectGroup = viewModel::selectGroup,
                        onSelectFavorites = viewModel::selectFavorites,
                        onOpenSettings = viewModel::openSettings,
                        onOpenGuide = viewModel::openGuide,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onStartMove = viewModel::startMovingFavorite,
                        onMove = viewModel::moveFavorite,
                        onFinishMove = viewModel::finishMovingFavorite,
                        onPlayNumber = viewModel::playNumber,
                    )
                }
            }
        }
    }
}
