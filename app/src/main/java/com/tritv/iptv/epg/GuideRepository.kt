package com.tritv.iptv.epg

import android.content.Context
import com.tritv.iptv.data.Channel
import com.tritv.iptv.data.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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
 * What a parse matched is kept too, so a start with the same file and channels skips parsing:
 * a whole guide takes seconds of a slow TV's CPU, the matched part a few hundred KB.
 */
class GuideRepository(context: Context) {

    private val prefs = context.getSharedPreferences("guide", Context.MODE_PRIVATE)
    private val cache = File(context.filesDir, "guide.xml")
    private val parsed = File(context.filesDir, "guide.parsed")

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
            val signature = signature(url, downloadedAt, channels)
            val programmes = readParsed(signature) ?: parse(channels).also { writeParsed(signature, it) }
            GuideData(programmes, downloadedAt)
        }

    /**
     * What the last parse of the cached file found for [channels], however old, or null when there
     * is none; reads a few hundred KB, no parsing.
     */
    suspend fun cached(url: String, channels: List<Channel>): GuideData? = withContext(Dispatchers.IO) {
        val downloadedAt = prefs.getLong(KEY_TIME, 0L)
        if (!cache.exists() || prefs.getString(KEY_URL, null) != url) return@withContext null
        readParsed(signature(url, downloadedAt, channels))?.let { GuideData(it, downloadedAt) }
    }

    /** What a parse depends on: the file (by address and download time) and what channels are matched by. */
    private fun signature(url: String, downloadedAt: Long, channels: List<Channel>): String {
        val names = channels.joinToString("\n") { c ->
            val tvgId = c.sources.firstNotNullOfOrNull { it.tvgId }.orEmpty()
            val tvgName = c.sources.firstNotNullOfOrNull { it.tvgName }.orEmpty()
            "${c.stableKey}\t${c.name}\t$tvgId\t$tvgName"
        }
        return "$PARSED_VERSION|$url|$downloadedAt|${names.hashCode()}|${channels.size}"
    }

    private fun readParsed(signature: String): Map<String, List<Programme>>? = runCatching {
        if (!parsed.exists()) return null
        DataInputStream(BufferedInputStream(parsed.inputStream(), 64 * 1024)).use { input ->
            if (input.readUTF() != signature) return null
            val result = HashMap<String, List<Programme>>()
            repeat(input.readInt()) {
                val key = input.readUTF()
                result[key] = List(input.readInt()) {
                    val start = input.readLong()
                    val stop = input.readLong()
                    val title = input.readUTF()
                    val desc = if (input.readBoolean()) input.readUTF() else null
                    Programme(start, stop, title, desc)
                }
            }
            result
        }
    }.getOrNull()

    private fun writeParsed(signature: String, programmes: Map<String, List<Programme>>) {
        runCatching {
            val tmp = File(parsed.parentFile, parsed.name + ".part")
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 64 * 1024)).use { out ->
                out.writeUTF(signature)
                out.writeInt(programmes.size)
                for ((key, list) in programmes) {
                    out.writeUTF(key)
                    out.writeInt(list.size)
                    for (p in list) {
                        out.writeLong(p.start)
                        out.writeLong(p.stop)
                        out.writeUTF(p.title.take(MAX_TEXT))
                        out.writeBoolean(p.desc != null)
                        p.desc?.let { out.writeUTF(it.take(MAX_TEXT)) }
                    }
                }
            }
            if (!tmp.renameTo(parsed)) {
                tmp.copyTo(parsed, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun download(url: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "tri.iptv/1.0")
            // Asked for by name, a gzip response is handed over as sent rather than unpacked, so the
            // cache keeps the few MB that came over the wire instead of tens of MB of XML; it is
            // unpacked as it is parsed.
            conn.setRequestProperty("Accept-Encoding", "gzip")
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
        private const val PARSED_VERSION = 1
        // writeUTF holds at most 65535 bytes; a CJK character takes three.
        private const val MAX_TEXT = 20_000
    }
}
