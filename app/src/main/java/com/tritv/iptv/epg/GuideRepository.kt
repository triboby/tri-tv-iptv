package com.tritv.iptv.epg

import android.content.Context
import com.tritv.iptv.data.Channel
import com.tritv.iptv.data.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Guide programmes per [Channel.stableKey], and when the file behind them was downloaded. */
data class GuideData(
    val programmes: Map<String, List<Programme>>,
    val downloadedAt: Long,
)

/**
 * Downloads the XMLTV guide (plain or gzip) to a cache file and parses the part the playlist
 * needs: 6 hours back to 3 days ahead, only channels that match. The file is fetched again when
 * it is older than [MAX_AGE_MS] or the address changed; a failed download falls back to the cache.
 */
class GuideRepository(context: Context) {

    private val prefs = context.getSharedPreferences("guide", Context.MODE_PRIVATE)
    private val cache = File(context.filesDir, "guide.xml")

    suspend fun load(url: String, channels: List<Channel>, force: Boolean = false): GuideData =
        withContext(Dispatchers.IO) {
            val cachedUrl = prefs.getString(KEY_URL, null)
            val cachedAt = prefs.getLong(KEY_TIME, 0L)
            val fresh = cache.exists() && cachedUrl == url && System.currentTimeMillis() - cachedAt < MAX_AGE_MS
            var downloadedAt = cachedAt
            if (force || !fresh) {
                try {
                    download(url)
                    downloadedAt = System.currentTimeMillis()
                    prefs.edit().putString(KEY_URL, url).putLong(KEY_TIME, downloadedAt).apply()
                } catch (e: Exception) {
                    // An older copy of the same guide still beats nothing.
                    if (!cache.exists() || cachedUrl != url) throw e
                }
            }
            GuideData(parse(channels), downloadedAt)
        }

    private fun download(url: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "TriTV/1.0")
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            val tmp = File(cache.parentFile, cache.name + ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(cache)) {
                tmp.copyTo(cache, overwrite = true)
                tmp.delete()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(channels: List<Channel>): Map<String, List<Programme>> {
        val now = System.currentTimeMillis()
        val matcher = EpgMatcher(channels)
        val names = HashMap<String, List<String>>()
        val guide = openMaybeGzip(cache.inputStream()).use { input ->
            XmltvParser.parse(input, from = now - KEEP_PAST_MS, to = now + KEEP_AHEAD_MS) { id, displayNames ->
                names[id] = displayNames
                matcher.wants(id, displayNames)
            }
        }
        return matcher.match(guide, names)
    }

    private fun openMaybeGzip(raw: InputStream): InputStream {
        val input = BufferedInputStream(raw, 64 * 1024)
        input.mark(2)
        val gzip = input.read() == 0x1f && input.read() == 0x8b
        input.reset()
        return if (gzip) BufferedInputStream(GZIPInputStream(input, 64 * 1024), 64 * 1024) else input
    }

    companion object {
        const val MAX_AGE_MS = 12 * 3_600_000L
        private const val KEEP_PAST_MS = 6 * 3_600_000L
        private const val KEEP_AHEAD_MS = 3 * 24 * 3_600_000L
        private const val KEY_URL = "url"
        private const val KEY_TIME = "downloaded_at"
    }
}
