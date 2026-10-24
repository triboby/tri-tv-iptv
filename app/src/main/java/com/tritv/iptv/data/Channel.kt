package com.tritv.iptv.data

/** A channel as shown to the user, backed by one or more playlist entries (backup lines). */
data class Channel(
    val name: String,
    val group: String?,
    /** Logo URLs from all its lines in playlist order, tried in turn until one loads. */
    val logos: List<String>,
    val sources: List<PlaylistEntry>,
) {
    // Both are worked out once: the list, favourites and guide look them up for every row on every
    // frame they recompose, and each takes a regex over the name.

    /** Identity that survives playlist reloads: tvg-id, else the normalized name. */
    val stableKey: String by lazy { ChannelMerger.keyOf(sources.first()) }

    /** What kind of channel this is ("科教" for CCTV-10), for names that don't say so themselves. */
    val category: String? by lazy { ChannelCategory.of(name) }
}
