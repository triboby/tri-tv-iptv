package com.tritv.iptv.data

/**
 * Tells an advert from a channel. Some redirect servers in the playlist now and then send a line
 * to a looping TV-shopping clip instead of the channel; it plays fine, so only its address or the
 * shape of its playlist gives it away.
 */
object AdFilter {

    /**
     * Address fragments of known advert streams, matched against every URL a line goes through
     * (redirects included), ignoring case. Add new ones here.
     */
    val AD_URL_PATTERNS = listOf(
        "/applive/mkt.m3u8",
        "/appadhw/",
        "//47.97.252.137:81/",
    )

    /** A playlist that ends (VOD) and runs shorter than this is a clip on a loop, not a channel. */
    const val MAX_CLIP_SECONDS = 10 * 60

    fun isAdUrl(url: String): Boolean = AD_URL_PATTERNS.any { url.contains(it, ignoreCase = true) }

    /**
     * Whether one HLS media playlist already shows a loop: it ends after a short clip, or it
     * lists the same segment twice.
     */
    fun isLoopPlaylist(playlist: String): Boolean {
        var ended = false
        var seconds = 0.0
        val segments = HashSet<String>()
        var repeated = false
        for (raw in playlist.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXT-X-ENDLIST") -> ended = true
                line.startsWith("#EXT-X-PLAYLIST-TYPE:VOD") -> ended = true
                line.startsWith("#EXTINF:") ->
                    seconds += line.removePrefix("#EXTINF:").substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                line.startsWith("#") -> {}
                !segments.add(line) -> repeated = true
            }
        }
        return repeated || (ended && segments.isNotEmpty() && seconds < MAX_CLIP_SECONDS)
    }

    /**
     * Watches the refreshes of one live media playlist for segments coming round again. A live
     * window slides on, so each segment keeps the sequence number it first had; a looped clip
     * hands out the same segments again under new numbers. Feed it each refresh with [check].
     */
    class LoopWatch {
        private val seen = LinkedHashMap<String, Long>()

        /** Remembers the segments of a refresh; true once one has come back under another number. */
        fun check(mediaSequence: Long, segmentUris: List<String>): Boolean {
            var loop = false
            segmentUris.forEachIndexed { i, uri ->
                val seq = mediaSequence + i
                val before = seen.put(uri, seq)
                if (before != null && before != seq) loop = true
            }
            while (seen.size > MAX_TRACKED) seen.remove(seen.keys.first())
            return loop
        }

        private companion object {
            /** Enough for several windows of an ordinary live playlist. */
            const val MAX_TRACKED = 256
        }
    }
}
