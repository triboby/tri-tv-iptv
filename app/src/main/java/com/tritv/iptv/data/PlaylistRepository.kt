package com.tritv.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class PlaylistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("playlist", Context.MODE_PRIVATE)

    var savedUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var lastChannelUrl: String?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_CHANNEL, value).apply()

    suspend fun load(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "TriTV/1.0")
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            M3uParser.parse(text)
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_LAST_CHANNEL = "last_channel"
    }
}
