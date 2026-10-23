package com.tritv.iptv.player

import androidx.media3.common.MimeTypes
import com.tritv.iptv.data.PlaylistEntry

/** One way of trying to play a channel: a source line and the MIME type to force (null lets ExoPlayer sniff). */
internal data class Attempt(val sourceIndex: Int, val mimeType: String?)

/** Every attempt for a channel in order: each source's candidate formats, then the next source. */
internal fun playbackAttempts(sources: List<PlaylistEntry>): List<Attempt> =
    sources.flatMapIndexed { i, source -> mimeCandidates(source.url).map { Attempt(i, it) } }

private val progressiveExtensions = setOf("mp4", "flv", "ts", "mkv", "webm", "mov", "mp3", "aac")

/**
 * Picks formats to try from the URL. IPTV links often have no extension or point at
 * scripts (.php, .ctv) that redirect to an HLS playlist, so those try HLS first and
 * fall back to letting ExoPlayer detect the container.
 */
internal fun mimeCandidates(url: String): List<String?> {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val ext = path.substringAfterLast('/').substringAfterLast('.', "")
    return when (ext) {
        "m3u8", "m3u" -> listOf(MimeTypes.APPLICATION_M3U8)
        "mpd" -> listOf(MimeTypes.APPLICATION_MPD)
        in progressiveExtensions -> listOf(null)
        else -> listOf(MimeTypes.APPLICATION_M3U8, null)
    }
}
