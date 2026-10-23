package com.tritv.iptv.data

/**
 * What is known about one stream URL (a channel's line): how fast it answered the last
 * probes and when it last worked or failed, by probe or by actual playback.
 */
data class LineStat(
    /** Time to the first byte of the response, smoothed over probes; null if never measured. */
    val latencyMs: Int? = null,
    val okAt: Long = 0L,
    val failAt: Long = 0L,
) {
    /** The latest result was a failure. */
    val failing: Boolean get() = failAt > okAt

    /** When the line was last checked either way. */
    val checkedAt: Long get() = maxOf(okAt, failAt)

    fun succeeded(at: Long, latency: Int? = null) = copy(
        okAt = at,
        // Halfway towards the new reading, so one slow answer does not reorder the lines.
        latencyMs = when {
            latency == null -> latencyMs
            latencyMs == null -> latency
            else -> (latencyMs + latency) / 2
        },
    )

    fun failed(at: Long) = copy(failAt = at)
}

object LineRanker {

    /**
     * Indices into [urls] in the order to try them: the line the viewer picked (unless it has
     * since failed), then working lines fastest first, then lines not checked yet, then lines
     * whose last check failed, those that worked most recently first. Ties keep the playlist order.
     */
    fun order(urls: List<String>, stats: Map<String, LineStat>, picked: String?): List<Int> {
        fun tier(i: Int): Int {
            val s = stats[urls[i]]
            return when {
                urls[i] == picked && s?.failing != true -> 0
                s == null || s.checkedAt == 0L -> 2
                s.failing -> 3
                else -> 1
            }
        }
        return urls.indices.sortedWith(
            compareBy<Int>(
                { tier(it) },
                { stats[urls[it]]?.takeIf { s -> !s.failing }?.latencyMs ?: Int.MAX_VALUE },
                // Among failed lines; zero for the rest.
                { -(stats[urls[it]]?.takeIf { s -> s.failing }?.okAt ?: 0L) },
                { it },
            ),
        )
    }

    /** Whether [url] can be probed with a plain HTTP request. */
    fun probeable(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    /** One stat per line, "latency|okAt|failAt" with an empty latency when unknown. */
    fun encode(stat: LineStat): String = "${stat.latencyMs ?: ""}|${stat.okAt}|${stat.failAt}"

    fun decode(text: String?): LineStat? {
        val parts = text?.split('|') ?: return null
        if (parts.size != 3) return null
        return LineStat(
            latencyMs = parts[0].toIntOrNull(),
            okAt = parts[1].toLongOrNull() ?: return null,
            failAt = parts[2].toLongOrNull() ?: return null,
        )
    }
}
