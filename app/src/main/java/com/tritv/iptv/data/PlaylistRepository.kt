package com.tritv.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** A loaded playlist: its channels and the guide address its header names, if any. */
data class Playlist(val channels: List<Channel>, val guideUrl: String?)

class PlaylistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("playlist", Context.MODE_PRIVATE)

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

    suspend fun load(url: String): Playlist = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "TriTV/1.0")
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Playlist(ChannelMerger.merge(M3uParser.parse(text)), M3uParser.guideUrl(text))
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_LAST_CHANNEL = "last_channel_key"
        const val KEY_GUIDE_URL = "guide_url"
        const val KEY_FAVORITES = "favorites"
    }
}
