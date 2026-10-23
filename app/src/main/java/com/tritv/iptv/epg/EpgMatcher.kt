package com.tritv.iptv.epg

import com.tritv.iptv.data.Channel

/**
 * Pairs playlist channels with XMLTV channels. In order: `tvg-id` equals the guide's channel id;
 * then `tvg-name`, the channel name, or the name's first word ("CCTV-1 综合" → "CCTV-1") equals a
 * `<display-name>` (or its first word); finally the name equals a guide id. Names are compared
 * [normalized][normalize], so "CCTV-1 综合", "CCTV1" and "ＣＣＴＶ１" can meet.
 */
class EpgMatcher(private val channels: List<Channel>) {

    private val channelIds = channels.mapNotNullTo(HashSet()) { c -> c.sources.firstNotNullOfOrNull { it.tvgId }?.let(::normalize) }
    private val channelNames = channels.flatMapTo(HashSet()) { nameKeys(it) }

    /** Whether the guide's channel [id] may belong to a playlist channel, so its programmes are worth keeping. */
    fun wants(id: String, displayNames: List<String>): Boolean {
        val key = normalize(id)
        if (key in channelIds || key in channelNames) return true
        return displayNames.any { n -> normalize(n) in channelNames || normalize(firstWord(n)) in channelNames }
    }

    /**
     * Guide programmes per [Channel.stableKey]. [displayNames] maps guide channel ids to their
     * display names; ids that only appear on programmes still match by id.
     */
    fun match(guide: Guide, displayNames: Map<String, List<String>>): Map<String, List<Programme>> {
        val byId = HashMap<String, String>()
        val byName = HashMap<String, String>()
        for (id in guide.keys) byId.putIfAbsent(normalize(id), id)
        for ((id, names) in displayNames) {
            if (id !in guide) continue
            for (n in names) byName.putIfAbsent(normalize(n), id)
        }
        for ((id, names) in displayNames) {
            if (id !in guide) continue
            for (n in names) byName.putIfAbsent(normalize(firstWord(n)), id)
        }

        val result = HashMap<String, List<Programme>>()
        for (channel in channels) {
            if (channel.stableKey in result) continue
            val tvgId = channel.sources.firstNotNullOfOrNull { it.tvgId }
            val id = tvgId?.let { byId[normalize(it)] }
                ?: nameKeys(channel).firstNotNullOfOrNull { byName[it] }
                ?: byId[normalize(channel.name)]
                ?: continue
            result[channel.stableKey] = guide.getValue(id)
        }
        return result
    }

    private fun nameKeys(channel: Channel): List<String> = buildList {
        channel.sources.firstNotNullOfOrNull { it.tvgName }?.let { add(normalize(it)) }
        add(normalize(channel.name))
        add(normalize(firstWord(channel.name)))
    }.filter { it.isNotEmpty() }.distinct()

    companion object {
        private val separators = Regex("""[\s\-_·.]+""")
        private val qualitySuffix = Regex("""(hd|fhd|uhd|高清|超清|标清|蓝光)$""")

        /** Lowercase, half-width, without separators or a trailing quality tag like "HD" or "高清". */
        fun normalize(name: String): String {
            val half = buildString(name.length) {
                for (c in name) {
                    append(
                        when (c) {
                            '　' -> ' '
                            in '！'..'～' -> c - 0xFEE0
                            else -> c
                        },
                    )
                }
            }
            val bare = half.lowercase().replace(separators, "")
            return bare.replace(qualitySuffix, "").ifEmpty { bare }
        }

        private fun firstWord(name: String) = name.trim().substringBefore(' ').substringBefore('　')
    }
}
