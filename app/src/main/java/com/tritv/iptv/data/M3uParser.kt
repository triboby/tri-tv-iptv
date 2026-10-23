package com.tritv.iptv.data

/**
 * Parses extended M3U playlists as used by IPTV providers:
 *
 * ```
 * #EXTM3U x-tvg-url="..."
 * #EXTINF:-1 tvg-id="cctv1" tvg-logo="..." group-title="央视",CCTV-1
 * #EXTVLCOPT:http-user-agent=Mozilla/5.0
 * http://host/live/cctv1.m3u8
 * ```
 */
object M3uParser {

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    /** The guide address in the `#EXTM3U` header (`x-tvg-url` or `url-tvg`); the first if it lists several. */
    fun guideUrl(text: String): String? {
        val header = text.lineSequence()
            .map { it.trim().removePrefix("\uFEFF") }
            .firstOrNull { it.isNotEmpty() }
            ?.takeIf { it.startsWith("#EXTM3U", ignoreCase = true) }
            ?: return null
        val attrs = attrRegex.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return (attrs["x-tvg-url"] ?: attrs["url-tvg"])
            ?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
    }

    fun parse(text: String): List<PlaylistEntry> {
        val channels = mutableListOf<PlaylistEntry>()
        var pending: Pending? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim().removePrefix("﻿")
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseExtInf(line)
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val p = pending ?: Pending()
                    if (p.group == null) p.group = line.substringAfter(':').trim().ifEmpty { null }
                    pending = p
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val p = pending ?: Pending()
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim().ifEmpty { null }
                    when (key) {
                        "http-user-agent" -> p.userAgent = value
                        "http-referrer", "http-referer" -> p.referrer = value
                    }
                    pending = p
                }
                line.startsWith("#") -> Unit
                else -> {
                    val p = pending ?: Pending()
                    channels += PlaylistEntry(
                        name = p.name ?: line.substringAfterLast('/'),
                        url = line,
                        group = p.group,
                        logo = p.logo,
                        tvgId = p.tvgId,
                        tvgName = p.tvgName,
                        userAgent = p.userAgent,
                        referrer = p.referrer,
                    )
                    pending = null
                }
            }
        }
        return channels
    }

    private fun parseExtInf(line: String): Pending {
        val body = line.substringAfter(':', "")
        val comma = firstCommaOutsideQuotes(body)
        val header = if (comma >= 0) body.substring(0, comma) else body
        val title = if (comma >= 0) body.substring(comma + 1).trim() else ""

        val attrs = attrRegex.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return Pending(
            name = title.ifEmpty { attrs["tvg-name"] }?.ifEmpty { null },
            group = attrs["group-title"]?.ifEmpty { null },
            logo = attrs["tvg-logo"]?.ifEmpty { null },
            tvgId = attrs["tvg-id"]?.ifEmpty { null },
            tvgName = attrs["tvg-name"]?.ifEmpty { null },
            userAgent = attrs["user-agent"] ?: attrs["http-user-agent"],
            referrer = attrs["http-referrer"] ?: attrs["referer"],
        )
    }

    private fun firstCommaOutsideQuotes(s: String): Int {
        var inQuotes = false
        s.forEachIndexed { i, c ->
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> return i
            }
        }
        return -1
    }

    private class Pending(
        var name: String? = null,
        var group: String? = null,
        var logo: String? = null,
        var tvgId: String? = null,
        var tvgName: String? = null,
        var userAgent: String? = null,
        var referrer: String? = null,
    )
}
