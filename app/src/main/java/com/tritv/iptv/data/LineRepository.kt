package com.tritv.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * Measures how fast each channel line answers, remembers it with the outcome of real
 * playback, and orders a channel's lines best first. Stats are kept per stream URL;
 * the line the viewer picked by hand is kept per [Channel.stableKey].
 */
class LineRepository(context: Context) {

    private val statPrefs = context.getSharedPreferences("line_stats", Context.MODE_PRIVATE)
    private val pickPrefs = context.getSharedPreferences("line_picks", Context.MODE_PRIVATE)

    private val _stats = MutableStateFlow(
        statPrefs.all.mapNotNull { (url, v) -> LineRanker.decode(v as? String)?.let { url to it } }.toMap(),
    )
    val stats: StateFlow<Map<String, LineStat>> = _stats.asStateFlow()

    private val inFlight: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val sweepLimit = Semaphore(SWEEP_CONCURRENCY)

    /** [channel]'s lines in the order to try them. */
    fun ordered(channel: Channel): List<PlaylistEntry> {
        if (channel.sources.size < 2) return channel.sources
        val urls = channel.sources.map { it.url }
        return LineRanker.order(urls, _stats.value, pickPrefs.getString(channel.stableKey, null))
            .map { channel.sources[it] }
    }

    /** Remembers [entry] as the line the viewer chose for [channel]; it is tried first while it works. */
    fun pick(channel: Channel, entry: PlaylistEntry) {
        pickPrefs.edit().putString(channel.stableKey, entry.url).apply()
    }

    /** Records how real playback of [entry] went. */
    fun report(entry: PlaylistEntry, ok: Boolean) = record(entry.url, ok, latency = null)

    /** Forgets lines that are no longer in the playlist. */
    fun prune(channels: List<Channel>) {
        val keep = channels.flatMapTo(HashSet()) { c -> c.sources.map { it.url } }
        val gone = _stats.value.keys - keep
        if (gone.isEmpty()) return
        _stats.update { it - gone }
        val editor = statPrefs.edit()
        for (url in gone) editor.remove(url)
        editor.apply()
    }

    /** Probes [channel]'s lines checked longer than [maxAgeMs] ago, all at once. */
    suspend fun probeChannel(channel: Channel, maxAgeMs: Long) = coroutineScope {
        staleLines(channel, maxAgeMs).map { async { probeAndRecord(it) } }.awaitAll()
    }

    /**
     * Probes the stale lines of every channel that has more than one, [SWEEP_CONCURRENCY]
     * at a time, in the order given, so put the channels most likely to be watched first.
     */
    suspend fun sweep(channels: List<Channel>, maxAgeMs: Long) = coroutineScope {
        channels.filter { it.sources.size > 1 }
            .flatMap { staleLines(it, maxAgeMs) }
            .distinctBy { it.url }
            .map { async { sweepLimit.withPermit { probeAndRecord(it) } } }
            .awaitAll()
    }

    private fun staleLines(channel: Channel, maxAgeMs: Long): List<PlaylistEntry> {
        val now = System.currentTimeMillis()
        return channel.sources.filter {
            LineRanker.probeable(it.url) && now - (_stats.value[it.url]?.checkedAt ?: 0L) > maxAgeMs
        }
    }

    private suspend fun probeAndRecord(entry: PlaylistEntry) {
        if (!inFlight.add(entry.url)) return
        try {
            val latency = probe(entry)
            record(entry.url, ok = latency != null, latency = latency)
        } finally {
            inFlight.remove(entry.url)
        }
    }

    private fun record(url: String, ok: Boolean, latency: Int?) {
        val now = System.currentTimeMillis()
        var updated: LineStat? = null
        _stats.update { all ->
            val old = all[url] ?: LineStat()
            val next = if (ok) old.succeeded(now, latency) else old.failed(now)
            updated = next
            all + (url to next)
        }
        updated?.let { statPrefs.edit().putString(url, LineRanker.encode(it)).apply() }
    }

    /**
     * Time in ms until the first byte of [entry]'s response arrives, following redirects,
     * or null if it fails, times out, or answers with an HTML error page.
     */
    private suspend fun probe(entry: PlaylistEntry): Int? = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        var url = URL(entry.url)
        repeat(MAX_REDIRECTS) {
            val conn = try {
                url.openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return@withContext null
            }
            try {
                conn.connectTimeout = PROBE_TIMEOUT_MS
                conn.readTimeout = PROBE_TIMEOUT_MS
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", entry.userAgent ?: PROBE_USER_AGENT)
                entry.referrer?.let { conn.setRequestProperty("Referer", it) }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return@withContext null
                    url = URL(url, location)
                    return@repeat
                }
                if (code !in 200..299) return@withContext null
                val first = conn.inputStream.read()
                if (first < 0 || first == '<'.code) return@withContext null
                return@withContext ((System.nanoTime() - start) / 1_000_000).toInt()
            } catch (e: Exception) {
                return@withContext null
            } finally {
                conn.disconnect()
            }
        }
        null
    }

    companion object {
        /** Probe stats older than this are refreshed by the background sweep. */
        const val SWEEP_MAX_AGE_MS = 30 * 60_000L
        /** Lines of the channel being opened are re-probed when older than this. */
        const val PLAY_MAX_AGE_MS = 2 * 60_000L
        private const val SWEEP_CONCURRENCY = 4
        private const val PROBE_TIMEOUT_MS = 3_000
        private const val MAX_REDIRECTS = 5
        private const val PROBE_USER_AGENT = "TriTV/1.0 (Linux; Android) ExoPlayer"
    }
}
