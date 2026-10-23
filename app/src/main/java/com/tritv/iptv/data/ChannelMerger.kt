package com.tritv.iptv.data

/**
 * Aggregated IPTV playlists list the same channel several times with different
 * stream URLs. This folds them into one [Channel] per group with all its sources.
 */
object ChannelMerger {

    // Some aggregators add a fake entry whose title is the playlist's update time.
    private val timestampName = Regex("""^\d{4}-\d{1,2}-\d{1,2}([ T]\d{1,2}:\d{2}(:\d{2})?)?$""")
    private val separators = Regex("""[\s_\-]+""")

    fun merge(entries: List<PlaylistEntry>): List<Channel> {
        val byKey = LinkedHashMap<String, MutableList<PlaylistEntry>>()
        for (entry in entries) {
            if (timestampName.matches(entry.name.trim())) continue
            byKey.getOrPut("${entry.group.orEmpty()}\u0000${keyOf(entry)}") { mutableListOf() } += entry
        }
        return byKey.values.map { group ->
            val sources = group.distinctBy { it.url }
            val first = sources.first()
            Channel(
                name = first.name,
                group = first.group,
                logo = sources.firstNotNullOfOrNull { it.logo },
                sources = sources,
            )
        }
    }

    fun keyOf(entry: PlaylistEntry): String =
        (entry.tvgId ?: entry.name).replace(separators, "").lowercase()
}
