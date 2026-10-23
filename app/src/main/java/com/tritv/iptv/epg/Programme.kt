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
 * Resolves overlaps in a list sorted by start time in favour of the programme that started first:
 * a later one starts when it ends, or is dropped if it ends by then. Guides seen so far get the
 * earlier entry right (e.g. 新花路放 21:10–22:17 is what airs, not 综艺喜乐汇 listed from 21:23).
 */
fun List<Programme>.withoutOverlaps(): List<Programme> {
    val result = ArrayList<Programme>(size)
    for (p in this) {
        val last = result.lastOrNull()
        when {
            last == null || p.start >= last.stop -> result += p
            p.stop > last.stop -> result += p.copy(start = last.stop)
        }
    }
    return result
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
