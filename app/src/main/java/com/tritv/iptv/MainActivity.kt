package com.tritv.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tritv.iptv.player.PlayerScreen
import com.tritv.iptv.ui.ChannelListScreen
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
                    PlayerScreen(
                        channel = state.channels[playing],
                        channelNumber = playing + 1,
                        onZap = viewModel::zap,
                        onExit = viewModel::stop,
                    )
                } else {
                    ChannelListScreen(
                        state = state,
                        onUrlChange = viewModel::onUrlChange,
                        onLoad = { viewModel.loadPlaylist() },
                        onPlay = viewModel::play,
                    )
                }
            }
        }
    }
}
