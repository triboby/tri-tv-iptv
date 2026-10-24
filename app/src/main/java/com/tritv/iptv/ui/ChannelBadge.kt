package com.tritv.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tritv.iptv.data.Channel
import com.tritv.iptv.data.ChannelCategory

/** 28sp semibold: the number, and the name when there is no logo. */
private val BadgeTitle = TriTvType.Tagline.copy(fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.2.sp)

/**
 * A channel's number and logo, as one unit wherever a channel is named on top of the picture (the
 * corner card and the info bar), so they look the same everywhere: the logo 44dp tall and 10dp
 * after the digits, or the name until a logo loads. With no [channel] the number leads nowhere.
 */
@Composable
fun ChannelBadge(number: String, channel: Channel?, modifier: Modifier = Modifier) {
    val logo = channel?.let { rememberLogo(it.logos) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Closer to a logo than to a name: the digits' own side bearing already adds to the gap.
        horizontalArrangement = Arrangement.spacedBy(if (logo != null) 10.dp else 12.dp),
        modifier = modifier.height(44.dp),
    ) {
        Text(number, style = BadgeTitle.tabular())
        when {
            channel == null -> NameText("没有这个频道", dim = true)
            logo != null -> ChannelLogo(logo, ChannelCategory.label(channel.name), height = 44.dp, maxWidth = 176.dp)
            else -> NameText(ChannelCategory.label(channel.name), dim = false)
        }
    }
}

@Composable
private fun NameText(text: String, dim: Boolean) {
    Text(
        text,
        style = BadgeTitle,
        color = if (dim) TriTvColors.TextDim else TriTvColors.Text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 200.dp),
    )
}
