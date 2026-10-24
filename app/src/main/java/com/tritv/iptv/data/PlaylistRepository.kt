package com.tritv.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A loaded playlist: its channels and the guide address its header names, if any.
 * [cachedAt] is set when the server could not be reached and the last copy was used instead.
 */
data class Playlist(val channels: List<Channel>, val guideUrl: String?, val cachedAt: Long? = null)

class PlaylistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("playlist", Context.MODE_PRIVATE)
    private val cache = File(context.filesDir, "playlist.m3u")

    var savedUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    /** [Channel.stableKey] of the last played channel. */
    var lastChannelKey: String?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_CHANNEL, value).apply()

    /** Guide (XMLTV) address the viewer typed; null to use the one in the playlist header. */
    var guideUrl: String?
        get() = prefs.getString(KEY_GUIDE_URL, null)
        set(value) = prefs.edit().putString(KEY_GUIDE_URL, value).apply()

    /** Favourite [Channel.stableKey]s in the viewer's order. */
    var favorites: List<String>
        get() = FavoriteList.decode(prefs.getString(KEY_FAVORITES, null))
        set(value) = prefs.edit().putString(KEY_FAVORITES, FavoriteList.encode(value)).apply()

    /**
     * The last copy downloaded from [url], or null if there is none. Shown at start-up while the
     * playlist is fetched again, so the channels do not wait on the network.
     */
    fun cached(url: String): Playlist? {
        if (prefs.getString(KEY_CACHE_URL, null) != url || !cache.exists()) return null
        return runCatching { parse(cache.readText(), cachedAt = prefs.getLong(KEY_CACHE_TIME, 0L)) }.getOrNull()
    }

    /**
     * Downloads and parses the playlist. For an address that has worked before, network errors
     * are retried a few times, since right after the TV wakes from standby its network takes a
     * while to come up; if it still fails, the last copy downloaded from it is used.
     */
    suspend fun load(url: String): Playlist = withContext(Dispatchers.IO) {
        val known = prefs.getString(KEY_CACHE_URL, null) == url && cache.exists()
        var attempt = 0
        while (true) {
            val text = try {
                download(url)
            } catch (e: IOException) {
                if (known && attempt < RETRY_DELAYS_MS.size) {
                    delay(RETRY_DELAYS_MS[attempt++])
                    continue
                }
                if (!known) throw e
                return@withContext parse(cache.readText(), cachedAt = prefs.getLong(KEY_CACHE_TIME, 0L))
            }
            runCatching {
                cache.writeText(text)
                prefs.edit().putString(KEY_CACHE_URL, url).putLong(KEY_CACHE_TIME, System.currentTimeMillis()).apply()
            }
            return@withContext parse(text, cachedAt = null)
        }
        error("unreachable")
    }

    private fun parse(text: String, cachedAt: Long?) =
        Playlist(ChannelMerger.merge(M3uParser.parse(text)), M3uParser.guideUrl(text), cachedAt)

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "tri.iptv/1.0")
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_LAST_CHANNEL = "last_channel_key"
        const val KEY_GUIDE_URL = "guide_url"
        const val KEY_FAVORITES = "favorites"
        const val KEY_CACHE_URL = "cache_url"
        const val KEY_CACHE_TIME = "cache_time"
        val RETRY_DELAYS_MS = longArrayOf(2_000, 4_000, 8_000)
    }
}
