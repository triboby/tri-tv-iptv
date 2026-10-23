package com.tritv.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Channel number typed with the remote's digit keys. */
@Stable
class NumberEntry internal constructor() {
    var digits by mutableStateOf("")
        internal set

    internal var committed: ((Int) -> Unit)? = null

    fun type(digit: Int) {
        digits = (digits + digit).takeLast(MAX_DIGITS)
    }

    /** Jumps now instead of waiting for the pause after the last digit. */
    fun commit() {
        val n = digits.toIntOrNull()
        digits = ""
        if (n != null && n > 0) committed?.invoke(n)
    }

    val active: Boolean get() = digits.isNotEmpty()

    private companion object {
        const val MAX_DIGITS = 4
    }
}

/** A [NumberEntry] that calls [onCommit] 1.5 s after the last digit. */
@Composable
fun rememberNumberEntry(onCommit: (Int) -> Unit): NumberEntry {
    val entry = remember { NumberEntry() }
    val latest by rememberUpdatedState(onCommit)
    entry.committed = { latest(it) }
    LaunchedEffect(entry.digits) {
        if (entry.digits.isEmpty()) return@LaunchedEffect
        delay(1_500)
        entry.commit()
    }
    return entry
}

/** The digit a remote key stands for, or null. */
fun digitOf(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

/** Pill showing a channel number being typed or zapped to, with the channel's name when it exists. */
@Composable
fun ChannelJumpPill(number: String, name: String?, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .height(56.dp)
            .background(TriTvColors.Ink.copy(alpha = 0.9f), Radius.Pill)
            .padding(horizontal = 24.dp),
    ) {
        Text(number, style = TriTvType.Display.tabular())
        Text(
            name ?: "没有这个频道",
            style = TriTvType.Tagline,
            color = if (name == null) TriTvColors.TextDim else TriTvColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}
