package com.tritv.iptv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

    // A playlist's ~90 logos at about 200 KB each once decoded.
    private val memory = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private const val RETRY_AFTER_MS = 10 * 60_000L

    // URL → when it failed, so a broken link is not retried on every focus move.
    private val failed = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Host → when it could not be reached at all (live.fanmingming.cn times out from some
    // networks): its other logos are skipped for a while instead of each waiting out the timeout.
    private val deadHosts = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun recent(map: Map<String, Long>, key: String?): Boolean {
        val at = key?.let { map[it] } ?: return false
        return System.currentTimeMillis() - at < RETRY_AFTER_MS
    }
    private val downloads = Semaphore(4)

    fun cached(urls: List<String>): ImageBitmap? = urls.firstNotNullOfOrNull { memory.get(it) }

    /** The first of [urls] that loads, or null if none does. */
    suspend fun load(context: Context, urls: List<String>): ImageBitmap? {
        for (url in urls) {
            memory.get(url)?.let { return it }
            if (recent(failed, url) || recent(deadHosts, hostOf(url))) continue
            val bitmap = withContext(Dispatchers.IO) { fromDisk(context, url) ?: download(context, url) }
            if (bitmap != null) {
                memory.put(url, bitmap)
                return bitmap
            }
            failed[url] = System.currentTimeMillis()
        }
        return null
    }

    /** Loads all [logoLists] (one per channel) into memory, at most four downloads at a time. */
    suspend fun preload(context: Context, logoLists: List<List<String>>) = coroutineScope {
        for (urls in logoLists) {
            if (urls.isNotEmpty() && cached(urls) == null) launch { load(context, urls) }
        }
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

    private fun hostOf(url: String): String? = runCatching { URL(url).host }.getOrNull()

    private class HttpStatusException(message: String) : IOException(message)

    private suspend fun download(context: Context, url: String): ImageBitmap? = downloads.withPermit {
        // Another download may have found this host dead while this one waited for a permit.
        if (recent(deadHosts, hostOf(url))) return@withPermit null
        try {
            val bytes = fetch(url)
            val bitmap = decode(bytes) ?: return@withPermit null
            val f = file(context, url)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            bitmap
        } catch (e: HttpStatusException) {
            Log.i(TAG, "logo failed: $url ($e)")
            null
        } catch (e: IOException) {
            // Timeout, refused, DNS: the host itself is unreachable, not just this file.
            hostOf(url)?.let { deadHosts[it] = System.currentTimeMillis() }
            Log.i(TAG, "logo host unreachable, skipping it: $url ($e)")
            null
        }
    }

    // HttpURLConnection won't follow a redirect between http and https, which logo hosts use.
    private fun fetch(start: String): ByteArray {
        var url = URL(start)
        repeat(4) {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2_500
            conn.readTimeout = 4_000
            conn.instanceFollowRedirects = false
            try {
                when (val code = conn.responseCode) {
                    in 200..299 -> conn.inputStream.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > MAX_BYTES) throw HttpStatusException("too large: ${bytes.size}")
                        return bytes
                    }
                    in 300..399 -> url = URL(url, conn.getHeaderField("Location") ?: throw HttpStatusException("redirect without Location"))
                    else -> throw HttpStatusException("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw HttpStatusException("too many redirects")
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
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return trimTransparent(bitmap).asImageBitmap()
    }

    // Logo sets pad their artwork with different amounts of transparent margin, so the same box
    // drew some logos large and some small. Cropping to the visible pixels evens that out.
    private fun trimTransparent(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (pixels[y * w + x] ushr 24 > 16) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0 || (left == 0 && top == 0 && right == w - 1 && bottom == h - 1)) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }
}

/**
 * The channel's logo once one of [urls] has loaded; null while loading or if none loads. Changing
 * [urls] drops the previous channel's logo at once rather than showing it until the new one loads.
 */
@Composable
fun rememberLogo(urls: List<String>): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val logo = remember(urls) { mutableStateOf(LogoCache.cached(urls)) }
    LaunchedEffect(urls) {
        if (logo.value == null && urls.isNotEmpty()) logo.value = LogoCache.load(context, urls)
    }
    return logo.value
}

/**
 * A logo drawn [height] tall, as wide as its shape needs up to [maxWidth], so every station's
 * logo reads at the same size whether it is square or a wide wordmark.
 */
@Composable
fun ChannelLogo(logo: ImageBitmap, description: String, height: Dp, maxWidth: Dp, modifier: Modifier = Modifier) {
    // Sized from the logo's own shape: left to its pixel size, the box could be wider than the
    // scaled artwork and centre it, leaving a gap on both sides.
    val width = (height * (logo.width.toFloat() / logo.height)).coerceAtMost(maxWidth)
    Image(
        bitmap = logo,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = modifier.size(width = width, height = height),
    )
}
