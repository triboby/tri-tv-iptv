package com.tritv.iptv.data

/** A channel as shown to the user, backed by one or more playlist entries (backup lines). */
data class Channel(
    val name: String,
    val group: String?,
    val logo: String?,
    val sources: List<PlaylistEntry>,
) {
    /** Identity that survives playlist reloads: tvg-id, else the normalized name. */
    val stableKey: String
        get() = ChannelMerger.keyOf(sources.first())
}
