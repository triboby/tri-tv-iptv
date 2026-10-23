package com.tritv.iptv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.tritv.iptv.R

/**
 * Apple-style dark palette: depth comes only from stepping the surface colour (black → ink → tile),
 * never from gradients, glows or shadows, and one blue carries everything interactive.
 */
object TriTvColors {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF1D1D1F)
    val Tile = Color(0xFF272729)
    val TileRaised = Color(0xFF2A2A2C)
    val Hairline = Color(0x14FFFFFF)

    /** Primary buttons. */
    val Action = Color(0xFF0066CC)

    /** Focus fill and selection ring. */
    val Focus = Color(0xFF0071E3)

    /** Links and live status on dark surfaces. */
    val SkyBlue = Color(0xFF2997FF)

    val Text = Color.White
    val TextMuted = Color(0xFFCCCCCC)
    val TextDim = Color(0xFF7A7A7A)
    val Error = Color(0xFFFF6B6B)
}

/** Inter (SIL OFL 1.1, see FONT-LICENSE-OFL.txt) for Latin and digits; Chinese falls back to the system font. */
val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/**
 * Type ladder in dp-equivalent sp. Weights are only 300 / 400 / 600; display sizes are tracked tight.
 * Every style uses tabular digits so channel numbers, counts and the clock line up.
 */
object TriTvType {
    val Display = TextStyle(fontFamily = Inter, fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.4).sp)
    val PlayerNumber = Display
    val Tagline = TextStyle(fontFamily = Inter, fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = 0.23.sp)
    val Body = TextStyle(fontFamily = Inter, fontFeatureSettings = "tnum", fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.37).sp)
    val Label = TextStyle(fontFamily = Inter, fontFeatureSettings = "tnum", fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.22).sp)
    val LabelStrong = Label.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val Caption = TextStyle(fontFamily = Inter, fontFeatureSettings = "tnum", fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (-0.22).sp)
    val Small = TextStyle(fontFamily = Inter, fontFeatureSettings = "tnum", fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = (-0.12).sp)
}

private val colors = darkColorScheme(
    primary = TriTvColors.Action,
    onPrimary = TriTvColors.Text,
    background = TriTvColors.Black,
    onBackground = TriTvColors.Text,
    surface = TriTvColors.Ink,
    onSurface = TriTvColors.Text,
    onSurfaceVariant = TriTvColors.TextMuted,
    error = TriTvColors.Error,
)

private val typography = Typography().run {
    copy(
        displaySmall = TriTvType.Display,
        titleLarge = TriTvType.Tagline,
        bodyLarge = TriTvType.Body,
        bodyMedium = TriTvType.Caption,
        bodySmall = TriTvType.Small,
        labelLarge = TriTvType.Label,
        labelMedium = TriTvType.Small,
    )
}

@Composable
fun TriTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // tv-material's Text defaults to black outside a Surface; make plain text white on our dark screens.
        CompositionLocalProvider(LocalContentColor provides TriTvColors.Text, content = content)
    }
}
