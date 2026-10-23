package com.tritv.iptv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val colors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    background = Color(0xFF0F141B),
    surface = Color(0xFF1A222D),
)

@Composable
fun TriTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
