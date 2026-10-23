package com.tritv.iptv.epg

/** One programme from the guide. Times are UTC epoch milliseconds; [stop] is exclusive. */
data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String? = null,
) {
    fun isOn(now: Long) = now in start until stop

    /** Share of the programme already shown at [now], 0..1. */
    fun progress(now: Long): Float =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/**
 * Ends each programme no later than the next one starts, from a list sorted by start time, so the
 * grid and the "now" line agree when a guide's times overlap (e.g. 21:10–22:17 then 21:23).
 */
fun List<Programme>.endAtNextStart(): List<Programme> = mapIndexed { i, p ->
    val next = getOrNull(i + 1)
    if (next != null && next.start < p.stop) p.copy(stop = next.start) else p
}

/** The programme on at [now] and the one after it, from a list sorted by start time. */
fun List<Programme>.nowAndNext(now: Long): Pair<Programme?, Programme?> {
    val i = indexOfFirst { it.stop > now }
    if (i < 0) return null to null
    val first = this[i]
    return if (first.start <= now) first to getOrNull(i + 1) else null to first
}

/** The programme covering [time], else the nearest one starting after it, else the last before it. */
fun List<Programme>.at(time: Long): Programme? =
    firstOrNull { it.isOn(time) } ?: firstOrNull { it.start > time } ?: lastOrNull()
