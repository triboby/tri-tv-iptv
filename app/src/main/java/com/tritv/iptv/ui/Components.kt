package com.tritv.iptv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/** The four corner radii in the system: small utility, list row, card, pill. */
object Radius {
    val Small = RoundedCornerShape(8.dp)
    val Row = RoundedCornerShape(11.dp)
    val Card = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** Horizontal safe margin for TV overscan. */
val ScreenMargin = 48.dp

private val logoPath = PathParser()
    .parsePathString("M-38,-52 Q-46,-56 -46,-46 L-46,46 Q-46,56 -38,52 L50,6 Q58,0 50,-6 Z")
    .toPath()

/** The Tri TV mark: three stacked play triangles, as in the launcher icon. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        // Same shapes as ic_launcher_foreground, scaled to fill the box: the three layers span about
        // 130 x 110 units, centred 9.5 units right of the path origin.
        val layers = listOf(
            Triple(-18f, 0.82f, listOf(Color(0xFF00E5C3), Color(0xFF1DE9B6)) to 0.85f),
            Triple(0f, 0.9f, listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)) to 0.85f),
            Triple(18f, 0.98f, listOf(Color(0xFF4FC3F7), Color(0xFF2979FF)) to 0.92f),
        )
        translate(left = center.x, top = center.y) {
            scale(this.size.minDimension / 132f, pivot = Offset.Zero) {
                for ((dx, s, paint) in layers) {
                    val (stops, alpha) = paint
                    translate(left = dx - 9.5f) {
                        scale(s, pivot = Offset.Zero) {
                            drawPath(
                                logoPath,
                                Brush.linearGradient(stops, start = Offset(-46f, -56f), end = Offset(58f, 56f)),
                                alpha = alpha,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A fixed-size box with [icon] (usually an emoji) centred in it, so text after it always starts at the same x. */
@Composable
fun IconSlot(icon: String?, slot: Dp, fontSize: TextUnit) {
    Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
        if (icon != null) Text(icon, fontSize = fontSize, lineHeight = fontSize, maxLines = 1)
    }
}

/** A group name with its emoji in a 16dp slot and a 6dp gap, for captions and status lines. */
@Composable
fun GroupCaption(group: String, style: TextStyle, color: Color) {
    val label = remember(group) { groupLabel(group) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.icon != null) IconSlot(label.icon, 16.dp, 13.sp)
        Text(label.name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun dateText(now: Long) = SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(now)
private fun timeText(now: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)

/** The current time, recomposing once a minute. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    return now
}

@Composable
fun ClockText(style: TextStyle, color: Color = TriTvColors.Text) {
    Text(timeText(rememberNow()), style = style, color = color)
}

/** Thin black bar at the very top: mark and name on the left, date and time on the right. */
@Composable
fun TopBar(trailing: @Composable RowScope.() -> Unit = {}) {
    val now = rememberNow()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(TriTvColors.Black)
            .padding(horizontal = ScreenMargin),
    ) {
        LogoMark(20.dp)
        Text("Tri TV", style = TriTvType.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.weight(1f))
        trailing()
        Text(dateText(now), style = TriTvType.Small, color = TriTvColors.TextMuted)
        Text(timeText(now), style = TriTvType.Small.copy(fontWeight = FontWeight.SemiBold))
    }
}

private val noGlow = Glow.None

/** Blue pill for the one main action on a screen. Focus adds a white ring; press shrinks it. */
@Composable
fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 22.dp),
        shape = ButtonDefaults.shape(Radius.Pill),
        colors = ButtonDefaults.colors(
            containerColor = TriTvColors.Action,
            contentColor = TriTvColors.Text,
            focusedContainerColor = TriTvColors.Focus,
            focusedContentColor = TriTvColors.Text,
            pressedContainerColor = TriTvColors.Focus,
            pressedContentColor = TriTvColors.Text,
            disabledContainerColor = TriTvColors.TileRaised,
            disabledContentColor = TriTvColors.TextDim,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, TriTvColors.Text), shape = Radius.Pill),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
        glow = ButtonDefaults.glow(glow = noGlow, focusedGlow = noGlow, pressedGlow = noGlow),
    ) {
        Text(text, style = TriTvType.Label)
    }
}

/** Ghost pill: blue outline and text, filled blue when focused. */
@Composable
fun SecondaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 22.dp),
        shape = ButtonDefaults.shape(Radius.Pill),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TriTvColors.SkyBlue,
            focusedContainerColor = TriTvColors.Focus,
            focusedContentColor = TriTvColors.Text,
            pressedContainerColor = TriTvColors.Focus,
            pressedContentColor = TriTvColors.Text,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, TriTvColors.SkyBlue), shape = Radius.Pill),
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
        glow = ButtonDefaults.glow(glow = noGlow, focusedGlow = noGlow, pressedGlow = noGlow),
    ) {
        Text(text, style = TriTvType.Label)
    }
}

/** Compact 8dp-radius button for secondary navigation, e.g. opening the source page. */
@Composable
fun UtilityButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 14.dp),
        shape = ButtonDefaults.shape(Radius.Small),
        colors = ButtonDefaults.colors(
            containerColor = TriTvColors.TileRaised,
            contentColor = TriTvColors.Text,
            focusedContainerColor = TriTvColors.Focus,
            focusedContentColor = TriTvColors.Text,
            pressedContainerColor = TriTvColors.Focus,
            pressedContentColor = TriTvColors.Text,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
        glow = ButtonDefaults.glow(glow = noGlow, focusedGlow = noGlow, pressedGlow = noGlow),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * A focusable row or chip with exact spacing: focus fills it blue, [selected] draws a 2dp blue ring,
 * press shrinks it to 0.95. No scale-up or glow on focus.
 */
@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = Radius.Row,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 20.dp),
    gap: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = SelectableSurfaceDefaults.shape(shape = shape),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TriTvColors.Text,
            focusedContainerColor = TriTvColors.Focus,
            focusedContentColor = TriTvColors.Text,
            pressedContainerColor = TriTvColors.Focus,
            pressedContentColor = TriTvColors.Text,
            selectedContainerColor = Color.Transparent,
            selectedContentColor = TriTvColors.Text,
            focusedSelectedContainerColor = TriTvColors.Focus,
            focusedSelectedContentColor = TriTvColors.Text,
            pressedSelectedContainerColor = TriTvColors.Focus,
            pressedSelectedContentColor = TriTvColors.Text,
        ),
        scale = SelectableSurfaceDefaults.scale(
            scale = 1f,
            focusedScale = 1f,
            pressedScale = 0.95f,
            selectedScale = 1f,
            focusedSelectedScale = 1f,
            pressedSelectedScale = 0.95f,
        ),
        border = SelectableSurfaceDefaults.border(
            selectedBorder = Border(BorderStroke(2.dp, TriTvColors.Focus), shape = shape),
        ),
        glow = SelectableSurfaceDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow.None,
            pressedGlow = Glow.None,
            selectedGlow = Glow.None,
            focusedSelectedGlow = Glow.None,
            pressedSelectedGlow = Glow.None,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Small key-cap label for remote hints ("OK", "返回"). */
@Composable
fun KeyCap(label: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(20.dp)
            .background(Color(0x2ED2D2D7), Radius.Small)
            .padding(horizontal = 6.dp),
    ) {
        Text(label, style = TriTvType.Small.copy(fontSize = 11.sp, lineHeight = 11.sp))
    }
}

/** Middle dot between items of a caption line. */
@Composable
fun Dot(color: Color = TriTvColors.TextMuted) {
    Text("·", style = TriTvType.Caption, color = color)
}
