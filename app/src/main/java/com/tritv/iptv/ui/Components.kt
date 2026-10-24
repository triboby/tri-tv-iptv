package com.tritv.iptv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/** The four corner radii in the system: small utility, list row, card, pill. */
object Radius {
    val Small = RoundedCornerShape(8.dp)
    val Row = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** Horizontal safe margin for TV overscan. */
val ScreenMargin = 48.dp

// One rounded play triangle: left edge 110 high, apex at 110 * phi / 2, corner radius 110 / phi^4.
private val logoPath = PathParser()
    .parsePathString(
        "M24.49,-39.87 L66.9,-13.65 A16.05,16.05 0 0 1 66.9,13.65 L24.49,39.87 " +
            "A16.05,16.05 0 0 1 0,26.21 L0,-26.21 A16.05,16.05 0 0 1 24.49,-39.87 Z",
    )
    .toPath()

/** Back to front: left offset, scale (each layer shrinks by phi^-1/5), gradient. */
private val logoLayers = listOf(
    Triple(-25.14f, 0.8249f, listOf(Color(0xFF1FD1B2), Color(0xFF0FA896))),
    Triple(-12.41f, 0.9082f, listOf(Color(0xFF8A63FF), Color(0xFF6A3DF0))),
    Triple(0f, 1f, listOf(Color(0xFF5AB8FF), Color(0xFF1F6FFF))),
)

/** The Tri TV mark: three stacked play triangles, as in the launcher icon. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    // Offscreen so the gap around each front layer can be cut out of the layers behind it.
    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        // Same shapes as ic_launcher_foreground, scaled to fill the box: the layers span about 100 x 85 units,
        // centred 21.4 units right of the front triangle's left edge.
        translate(left = center.x, top = center.y) {
            scale(this.size.minDimension / 100f, pivot = Offset.Zero) {
                for ((i, layer) in logoLayers.withIndex()) {
                    val (dx, s, stops) = layer
                    translate(left = dx - 21.4f) {
                        scale(s, pivot = Offset.Zero) {
                            if (i > 0) {
                                drawPath(
                                    logoPath,
                                    Color.Black,
                                    style = Stroke(width = 3.7f / s, join = StrokeJoin.Round),
                                    blendMode = BlendMode.Clear,
                                )
                            }
                            drawPath(
                                logoPath,
                                Brush.linearGradient(stops, start = Offset(0f, -42.26f), end = Offset(74.51f, 42.26f)),
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
        if (label.icon != null) IconSlot(label.icon, 16.dp, 14.sp)
        Text(label.name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
    Text(timeText(rememberNow()), style = style.tabular(), color = color)
}

/**
 * The one bar at the top of a screen: [content] on the left (title, actions), then a hairline divider
 * and the clock at the far right, everything centred in 68dp.
 */
@Composable
fun TitleBar(spacing: Dp = 16.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(TriTvColors.Ink.copy(alpha = 0.8f))
            .drawBehind {
                drawLine(TriTvColors.Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = ScreenMargin),
    ) {
        content()
        Box(
            Modifier
                .width(1.dp)
                .height(20.dp)
                .background(TriTvColors.Text.copy(alpha = 0.16f)),
        )
        ClockText(TriTvType.Tagline.copy(lineHeight = 24.sp))
    }
}

/** The mark and a title, for the left end of a [TitleBar]. */
@Composable
fun TitleWithMark(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LogoMark(36.dp)
        Text(title, style = TriTvType.Tagline.copy(lineHeight = 24.sp))
    }
}

private val noGlow = Glow.None

/**
 * Blue pill for the one main action on a screen. It is already blue, so focus inverts it to white
 * with blue text (a blue fill would be indistinguishable); press shrinks it.
 */
@Composable
fun PrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Shown in place of [text] with a spinner while the action runs; the pill keeps its focus and size. */
    busyText: String? = null,
    /** Shown with a check once the action has succeeded. */
    doneText: String? = null,
    /** Fixed width, with the label centred, so swapping it for the spinner does not move the row. */
    width: Dp? = null,
) {
    val label = when {
        doneText != null -> PillLabel.Done
        busyText != null -> PillLabel.Busy
        else -> PillLabel.Idle
    }
    Button(
        // Ignore presses while busy instead of disabling, which would drop focus and turn the pill grey.
        onClick = { if (label == PillLabel.Idle) onClick() },
        enabled = enabled,
        modifier = modifier
            .height(40.dp)
            .then(if (width != null) Modifier.width(width) else Modifier),
        contentPadding = PaddingValues(horizontal = 22.dp),
        shape = ButtonDefaults.shape(Radius.Pill),
        colors = ButtonDefaults.colors(
            containerColor = TriTvColors.Action,
            contentColor = TriTvColors.Text,
            focusedContainerColor = TriTvColors.Text,
            focusedContentColor = TriTvColors.Action,
            pressedContainerColor = TriTvColors.Text,
            pressedContentColor = TriTvColors.Action,
            disabledContainerColor = TriTvColors.TileRaised,
            disabledContentColor = TriTvColors.TextDim,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
        glow = ButtonDefaults.glow(glow = noGlow, focusedGlow = noGlow, pressedGlow = noGlow),
    ) {
        val fill = if (width != null) Modifier.fillMaxWidth() else Modifier
        Crossfade(label, modifier = fill, animationSpec = tween(150), label = "pill") { shown ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = fill,
            ) {
                val color = LocalContentColor.current
                when (shown) {
                    PillLabel.Idle -> Text(text, style = TriTvType.Label)
                    PillLabel.Busy -> {
                        Spinner(size = 16.dp, stroke = 2.dp, color = color, trackColor = color.copy(alpha = 0.3f))
                        Text(busyText ?: text, style = TriTvType.Label)
                    }
                    PillLabel.Done -> {
                        CheckIcon(16.dp, color)
                        Text(doneText ?: text, style = TriTvType.Label)
                    }
                }
            }
        }
    }
}

private enum class PillLabel { Idle, Busy, Done }

/** Stroke check mark, for a finished import and status cards. */
@Composable
fun CheckIcon(size: Dp = 16.dp, color: Color = TriTvColors.SkyBlue) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = 2.5.dp.toPx()
        drawLine(color, Offset(w * 0.12f, w * 0.52f), Offset(w * 0.4f, w * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, w * 0.8f), Offset(w * 0.9f, w * 0.25f), stroke, StrokeCap.Round)
    }
}

/** Ghost pill: blue outline and text; focus turns it white with blue text, like [PrimaryPill]. */
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
            focusedContainerColor = TriTvColors.Text,
            focusedContentColor = TriTvColors.Action,
            pressedContainerColor = TriTvColors.Text,
            pressedContentColor = TriTvColors.Action,
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
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    shape: Shape = Radius.Row,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 20.dp),
    gap: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
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

private fun iconPath(d: String) = PathParser().parsePathString(d).toPath()

private val linkPaths = listOf(
    iconPath("M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"),
    iconPath("M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"),
)
private val rssPaths = listOf(iconPath("M4 11a9 9 0 0 1 9 9"), iconPath("M4 4a16 16 0 0 1 16 16"))
private val guidePaths = listOf(iconPath("M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"), iconPath("M3 10h18"), iconPath("M9 10v10"))
private val starPath = iconPath("M12 2.5l2.9 6.1 6.6.8-4.9 4.6 1.3 6.6L12 17.3l-5.9 3.3 1.3-6.6-4.9-4.6 6.6-.8z")
private val arrowsPaths = listOf(iconPath("M8 9l4-4 4 4"), iconPath("M8 15l4 4 4-4"))

/** A 24-unit line icon drawn with 2-unit round strokes, scaled to [size]. */
@Composable
private fun StrokeIcon(paths: List<androidx.compose.ui.graphics.Path>, size: Dp, color: Color, dot: Offset? = null) {
    Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, pivot = Offset.Zero) {
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            paths.forEach { drawPath(it, color, style = stroke) }
            dot?.let { drawCircle(color, radius = 1.5f, center = it) }
        }
    }
}

@Composable
fun LinkIcon(size: Dp = 16.dp, color: Color = LocalContentColor.current) = StrokeIcon(linkPaths, size, color)

@Composable
fun RssIcon(size: Dp = 16.dp, color: Color = LocalContentColor.current) = StrokeIcon(rssPaths, size, color, Offset(5f, 19f))

/** A table with a header row and a first column: the programme guide. */
@Composable
fun GuideIcon(size: Dp = 16.dp, color: Color = LocalContentColor.current) = StrokeIcon(guidePaths, size, color)

/** Up and down chevrons: a favourite that is being moved. */
@Composable
fun MoveIcon(size: Dp = 16.dp, color: Color = LocalContentColor.current) = StrokeIcon(arrowsPaths, size, color)

/** Five-point star, filled for a favourite or outlined. */
@Composable
fun StarIcon(size: Dp = 14.dp, color: Color = LocalContentColor.current, filled: Boolean = true) {
    Canvas(Modifier.size(size)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) {
            if (filled) drawPath(starPath, color)
            else drawPath(starPath, color, style = Stroke(width = 2f, join = StrokeJoin.Round))
        }
    }
}

/** 4dp pill track filled to [progress] (0..1) in sky blue. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.height(4.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(Color(0x29FFFFFF), cornerRadius = r)
        val w = size.width * progress.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(TriTvColors.SkyBlue, size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = r)
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
        Text(label, style = TriTvType.Small.copy(fontSize = 12.sp, lineHeight = 12.sp))
    }
}

/** Middle dot between items of a caption line. */
@Composable
fun Dot(color: Color = TriTvColors.TextMuted) {
    Text("·", style = TriTvType.Caption, color = color)
}

/**
 * Loading ring: a quarter arc turning once every 0.9 s at a steady speed over a faint track.
 * The only thing on screen that keeps moving while something loads.
 */
@Composable
fun Spinner(
    size: Dp = 40.dp,
    stroke: Dp = 3.dp,
    color: Color = TriTvColors.SkyBlue,
    trackColor: Color = Color(0x24FFFFFF),
    modifier: Modifier = Modifier,
) {
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "turn",
    )
    Canvas(modifier.size(size)) {
        val w = stroke.toPx()
        val inset = w / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
        drawCircle(trackColor, radius = (this.size.minDimension - w) / 2, style = Stroke(w))
        rotate(turn) {
            drawArc(color, -90f, 90f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/** Placeholder bars breathe between full and 55% opacity, 1.2 s each way, while the real content loads. */
@Composable
fun rememberSkeletonAlpha(): Float {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label = "alpha",
    )
    return alpha
}

/** Ease-out for things arriving, ease-in for things leaving (motion spec on the design canvas). */
val EnterEasing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f)
val ExitEasing = androidx.compose.animation.core.CubicBezierEasing(0.42f, 0f, 1f, 1f)
