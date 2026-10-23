package com.tritv.iptv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Station logos named by the playlist's tvg-logo, downloaded once into the cache directory and kept
 * decoded in memory. Logos are not bundled with the app: they are the stations' trademarks.
 */
object LogoCache {

    private const val TAG = "TriTV-Logos"
    private const val MAX_BYTES = 512 * 1024

    private val memory = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    // URLs that failed this session, so a dead host is not retried on every scroll.
    private val failed = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val downloads = Semaphore(4)

    fun cached(urls: List<String>): ImageBitmap? = urls.firstNotNullOfOrNull { memory.get(it) }

    /** The first of [urls] that loads, or null if none does. */
    suspend fun load(context: Context, urls: List<String>): ImageBitmap? {
        for (url in urls) {
            memory.get(url)?.let { return it }
            if (url in failed) continue
            val bitmap = withContext(Dispatchers.IO) { fromDisk(context, url) ?: download(context, url) }
            if (bitmap != null) {
                memory.put(url, bitmap)
                return bitmap
            }
            failed += url
        }
        return null
    }

    private fun file(context: Context, url: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "logos"), hash)
    }

    private fun fromDisk(context: Context, url: String): ImageBitmap? {
        val f = file(context, url)
        return if (f.exists()) decode(f.readBytes()) else null
    }

    private suspend fun download(context: Context, url: String): ImageBitmap? = downloads.withPermit {
        try {
            val bytes = fetch(url)
            val bitmap = decode(bytes) ?: return@withPermit null
            val f = file(context, url)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            bitmap
        } catch (e: IOException) {
            Log.i(TAG, "logo failed: $url ($e)")
            null
        }
    }

    // HttpURLConnection won't follow a redirect between http and https, which logo hosts use.
    private fun fetch(start: String): ByteArray {
        var url = URL(start)
        repeat(4) {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 8_000
            conn.instanceFollowRedirects = false
            try {
                when (val code = conn.responseCode) {
                    in 200..299 -> conn.inputStream.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > MAX_BYTES) throw IOException("too large: ${bytes.size}")
                        return bytes
                    }
                    in 300..399 -> url = URL(url, conn.getHeaderField("Location") ?: throw IOException("redirect without Location"))
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("too many redirects")
    }

    // Logos come as large as 640×320; halve those, they are drawn at most ~220 px wide.
    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = if (bounds.outWidth >= 480) 2 else 1
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}

/** The channel's logo once one of [urls] has loaded; null while loading or if none loads. */
@Composable
fun rememberLogo(urls: List<String>): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val logo by produceState(LogoCache.cached(urls), urls) {
        if (value == null && urls.isNotEmpty()) value = LogoCache.load(context, urls)
    }
    return logo
}

/** A logo scaled to fit its box, left-aligned so it lines up with the text around it. */
@Composable
fun ChannelLogo(logo: ImageBitmap, description: String, modifier: Modifier = Modifier, alignment: Alignment = Alignment.CenterStart) {
    Image(
        bitmap = logo,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        alignment = alignment,
        modifier = modifier,
    )
}
