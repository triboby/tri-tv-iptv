package com.tritv.iptv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

val PillShape = RoundedCornerShape(21.dp)
val CardShape = RoundedCornerShape(16.dp)
val PanelShape = RoundedCornerShape(20.dp)

private val glassBrush = Brush.verticalGradient(listOf(TriTvColors.GlassTop, TriTvColors.GlassBottom))
private val panelBrush = Brush.verticalGradient(listOf(TriTvColors.PanelTop, TriTvColors.PanelBottom))

/** Frosted translucent surface with a hairline edge, for unfocused controls. */
fun Modifier.glass(shape: Shape = PillShape) =
    background(glassBrush, shape).border(1.dp, TriTvColors.GlassStroke, shape)

/** Dark frosted panel for overlays on top of video. */
fun Modifier.glassPanel(shape: Shape = PanelShape) =
    background(panelBrush, shape).border(1.dp, TriTvColors.PanelStroke, shape)

/**
 * A stable, muted colour for [key] (e.g. a channel or group name) so the ambient glow shifts as
 * focus moves, like the launcher glowing in the focused app's colour.
 */
fun ambientColorFor(key: String?): Color {
    if (key == null) return TriTvColors.GlowTeal
    val hue = Math.floorMod(key.hashCode(), 360).toFloat()
    return Color.hsv(hue, 0.65f, 0.55f)
}

/** Near-black backdrop lit by a soft glow at the top right and a faint violet one at the bottom left. */
@Composable
fun AmbientBackground(
    glow: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val animatedGlow by animateColorAsState(glow, tween(durationMillis = 700), label = "ambient")
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TriTvColors.Base)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(animatedGlow.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.78f, size.height * 0.05f),
                        radius = size.width * 0.6f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(TriTvColors.GlowViolet.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(size.width * 0.05f, size.height * 1.05f),
                        radius = size.width * 0.45f,
                    ),
                )
                // Darken towards the bottom so lists stay readable.
                drawRect(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.6f),
                    ),
                )
            },
        content = content,
    )
}

/** Translucent pill that turns solid white with dark text when focused. */
@Composable
fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        shape = ButtonDefaults.shape(PillShape),
        colors = ButtonDefaults.colors(
            containerColor = TriTvColors.GlassTop,
            contentColor = TriTvColors.Text,
            focusedContainerColor = TriTvColors.Focus,
            focusedContentColor = TriTvColors.OnFocus,
            pressedContainerColor = TriTvColors.Focus,
            pressedContentColor = TriTvColors.OnFocus,
            disabledContainerColor = TriTvColors.GlassBottom,
            disabledContentColor = TriTvColors.TextDim,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, TriTvColors.GlassStroke), shape = PillShape),
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        glow = ButtonDefaults.glow(focusedGlow = Glow(TriTvColors.Focus.copy(alpha = 0.35f), 8.dp)),
        content = content,
    )
}

/** Shared look for focusable rows: frosted when idle, white when focused, accent tint when selected. */
object TriTvListItem {
    @Composable
    fun colors() = ListItemDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = TriTvColors.Text,
        focusedContainerColor = TriTvColors.Focus,
        focusedContentColor = TriTvColors.OnFocus,
        pressedContainerColor = TriTvColors.Focus,
        pressedContentColor = TriTvColors.OnFocus,
        selectedContainerColor = TriTvColors.Selected,
        selectedContentColor = TriTvColors.Text,
        focusedSelectedContainerColor = TriTvColors.Focus,
        focusedSelectedContentColor = TriTvColors.OnFocus,
    )

    @Composable
    fun shape(shape: Shape = CardShape) = ListItemDefaults.shape(shape = shape)

    @Composable
    fun scale() = ListItemDefaults.scale(focusedScale = 1.03f)

    @Composable
    fun glow() = ListItemDefaults.glow(focusedGlow = Glow(TriTvColors.Focus.copy(alpha = 0.3f), 10.dp))
}

private fun greetingFor(hour: Int) = when (hour) {
    in 5..10 -> "早上好"
    in 11..12 -> "中午好"
    in 13..17 -> "下午好"
    else -> "晚上好"
}

/** Greeting, large clock and date, ticking once a minute. */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    val calendar = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time) }
    val date = remember(now) { SimpleDateFormat("M月d日  EEEE", Locale.CHINA).format(calendar.time) }

    Column(modifier) {
        Text(greetingFor(calendar.get(Calendar.HOUR_OF_DAY)), style = EyebrowStyle)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                time,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp, lineHeight = 56.sp),
                color = TriTvColors.Text,
            )
            Box(
                Modifier
                    .padding(horizontal = 18.dp)
                    .width(1.dp)
                    .height(30.dp)
                    .background(TriTvColors.GlassStroke),
            )
            Text(date, style = EyebrowStyle.copy(fontSize = 15.sp))
        }
    }
}
