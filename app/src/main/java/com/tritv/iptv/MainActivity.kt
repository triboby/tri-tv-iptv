package com.tritv.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                if (state.showGuide && state.channels.isNotEmpty()) {
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
                    // One player for the drawer and full screen, so OK on the playing channel goes
                    // full screen without loading it again.
                    Box(Modifier.fillMaxSize()) {
                        val playing = state.playingIndex
                        if (playing != null && playing in state.channels.indices) {
                            val channel = state.channels[playing]
                            val lineStats by viewModel.lineStats.collectAsStateWithLifecycle()
                            PlayerScreen(
                                channel = channel,
                                lines = state.playingLines.ifEmpty { channel.sources },
                                lineStats = lineStats,
                                onLineResult = viewModel::reportLine,
                                onLinePicked = viewModel::pickLine,
                                channelNumber = playing + 1,
                                programmes = state.programmesOf(channel),
                                isFavorite = state.isFavorite(channel),
                                onToggleFavorite = { viewModel.toggleFavorite(channel.stableKey) },
                                onOpenGuide = viewModel::openGuide,
                                zapTarget = viewModel::zapTarget,
                                channelAt = { n -> state.channels.getOrNull(n - 1) },
                                programmesOf = state::programmesOf,
                                onZap = viewModel::zap,
                                onPlayNumber = viewModel::playNumber,
                                onExit = viewModel::openList,
                                behindDrawer = state.listOpen,
                            )
                        }
                        if (state.listOpen || playing == null) {
                            ChannelListScreen(
                                state = state,
                                onPlay = viewModel::play,
                                onPreview = viewModel::preview,
                                onClose = viewModel::closeList,
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
    }
}
