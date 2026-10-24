package com.tritv.iptv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.delay

/** Channel number typed with the remote's digit keys. */
@Stable
class NumberEntry internal constructor() {
    var digits by mutableStateOf("")
        internal set

    internal var committed: ((Int) -> Unit)? = null

    /** Counts jumps made by number, so the player can tell a typed jump from any other channel change. */
    var jumps = 0
        private set

    fun type(digit: Int) {
        digits = (digits + digit).takeLast(MAX_DIGITS)
    }

    /** Jumps now instead of waiting for the pause after the last digit. */
    fun commit() {
        val n = digits.toIntOrNull()
        digits = ""
        if (n != null && n > 0) {
            jumps++
            committed?.invoke(n)
        }
    }

    /** Drops the digits typed so far without jumping. */
    fun clear() {
        digits = ""
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
