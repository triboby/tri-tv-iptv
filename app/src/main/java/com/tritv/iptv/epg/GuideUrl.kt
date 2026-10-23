package com.tritv.iptv.epg

import java.net.URI

private val loopbackHosts = setOf("127.0.0.1", "localhost", "0.0.0.0", "::1", "[::1]")

/**
 * Playlist servers running on a home computer often write their own guide address as
 * `http://127.0.0.1:8000/epg.xml`, which on the TV points at the TV itself. Such an address is
 * moved to the host the playlist came from, keeping its port and path; anything else is unchanged.
 */
fun resolveGuideUrl(guideUrl: String, playlistUrl: String): String {
    val guide = runCatching { URI(guideUrl.trim()) }.getOrNull() ?: return guideUrl
    val host = guide.host?.lowercase() ?: return guideUrl
    if (host !in loopbackHosts) return guideUrl
    val playlistHost = runCatching { URI(playlistUrl.trim()).host }.getOrNull()
    if (playlistHost.isNullOrEmpty() || playlistHost.lowercase() in loopbackHosts) return guideUrl
    return runCatching {
        URI(guide.scheme, guide.userInfo, playlistHost, guide.port, guide.path, guide.query, guide.fragment).toString()
    }.getOrDefault(guideUrl)
}
