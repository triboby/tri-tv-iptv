package com.tritv.iptv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.tritv.iptv.R

/**
 * Dark "frosted glass" look in the style of a tvOS home screen: near-black base lit by a soft
 * ambient glow, translucent white surfaces, and a solid white focus with dark text.
 */
object TriTvColors {
    val Base = Color(0xFF07090D)
    val GlowTeal = Color(0xFF0E7C86)
    val GlowViolet = Color(0xFF4B3A8C)

    /** Translucent white surface, top and bottom of its gradient. */
    val GlassTop = Color(0x2EFFFFFF)
    val GlassBottom = Color(0x14FFFFFF)
    val GlassStroke = Color(0x38FFFFFF)

    /** Opaque-ish dark panel for overlays on top of video. */
    val PanelTop = Color(0xF22A2A2E)
    val PanelBottom = Color(0xF5161618)
    val PanelStroke = Color(0x33FFFFFF)

    val Focus = Color(0xF5FFFFFF)
    val OnFocus = Color(0xFF111111)
    val Accent = Color(0xFF6EC1FF)
    val Selected = Color(0x596EC1FF)

    val Text = Color.White
    val TextMid = Color(0xB3FFFFFF)
    val TextDim = Color(0x80FFFFFF)
    val Error = Color(0xFFFF6B6B)
}

/**
 * Outfit (SIL OFL 1.1, see FONT-LICENSE-OFL.txt) for Latin letters and digits: the clock,
 * channel numbers and titles. Chinese glyphs fall back to the system font.
 */
@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: Int) = Font(
    R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Outfit = FontFamily(outfit(200), outfit(300), outfit(400), outfit(500))

private val colors = darkColorScheme(
    primary = TriTvColors.Accent,
    onPrimary = TriTvColors.OnFocus,
    background = TriTvColors.Base,
    onBackground = TriTvColors.Text,
    surface = Color(0xFF1C1C1E),
    onSurface = TriTvColors.Text,
    onSurfaceVariant = TriTvColors.TextMid,
    error = TriTvColors.Error,
)

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Light),
        displayMedium = displayMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Light),
        displaySmall = displaySmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Light),
        headlineLarge = headlineLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Normal),
        headlineMedium = headlineMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Normal),
        headlineSmall = headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Normal),
        titleLarge = titleLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = 0.01.em),
    )
}

/** Small spaced-out caps line, like the greeting above the clock. */
val EyebrowStyle = TextStyle(
    fontFamily = Outfit,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    letterSpacing = 0.2.em,
    color = TriTvColors.TextMid,
)

@Composable
fun TriTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // tv-material's Text defaults to black outside a Surface; make plain text white on our dark screens.
        CompositionLocalProvider(LocalContentColor provides TriTvColors.Text, content = content)
    }
}
