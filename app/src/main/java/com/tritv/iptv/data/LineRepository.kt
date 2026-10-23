package com.tritv.iptv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
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
    // Probes run here so one stuck on a socket can be abandoned without blocking its caller.
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [channel]'s lines in the order to try them. */
    fun ordered(channel: Channel): List<PlaylistEntry> {
        if (channel.sources.size < 2) return channel.sources
        val urls = channel.sources.map { it.url }
        val stats = _stats.value
        val order = LineRanker.order(urls, stats, pickPrefs.getString(channel.stableKey, null))
        Log.i(TAG, "order ${channel.name}: " + order.joinToString { i -> "${i + 1}=" + describe(stats[urls[i]]) })
        return order.map { channel.sources[it] }
    }

    private fun describe(stat: LineStat?): String = when {
        stat == null || stat.checkedAt == 0L -> "?"
        stat.failing -> "dead"
        else -> "${stat.latencyMs ?: "ok"}"
    }

    /** Remembers [entry] as the line the viewer chose for [channel]; it is tried first while it works. */
    fun pick(channel: Channel, entry: PlaylistEntry) {
        pickPrefs.edit().putString(channel.stableKey, entry.url).apply()
    }

    /** Records how real playback of [entry] went. */
    fun report(entry: PlaylistEntry, ok: Boolean) {
        Log.i(TAG, (if (ok) "played " else "play failed ") + entry.url)
        record(entry.url, ok, latency = null)
    }

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
     * Probes [entry] with [LineProbe], giving up after [PROBE_DEADLINE_MS] whatever the socket
     * timeouts do: a host that resolves to several dead addresses, or a slow DNS answer, can
     * otherwise hold a probe far longer than the player takes to give up on its own.
     */
    private suspend fun probe(entry: PlaylistEntry): Int? {
        val conns = Collections.synchronizedList(mutableListOf<HttpURLConnection>())
        val job = probeScope.async { runCatching { LineProbe.run(entry, PROBE_TIMEOUT_MS) { conns += it } }.getOrNull() }
        val latency = withTimeoutOrNull(PROBE_DEADLINE_MS) { job.await() }
        if (job.isActive) {
            job.cancel()
            synchronized(conns) { conns.forEach { runCatching { it.disconnect() } } }
        }
        Log.i(TAG, (if (latency != null) "ok ${latency}ms " else "dead ") + entry.url)
        return latency
    }

    companion object {
        /** Probe stats older than this are refreshed by the background sweep. */
        const val SWEEP_MAX_AGE_MS = 30 * 60_000L
        /** Lines of the channel being opened are re-probed when older than this. */
        const val PLAY_MAX_AGE_MS = 2 * 60_000L
        private const val SWEEP_CONCURRENCY = 6
        /** Socket connect and read timeout for each request of a probe. */
        private const val PROBE_TIMEOUT_MS = 3_000
        /** Most a whole probe (playlist, variant, segment, redirects) may take. */
        private const val PROBE_DEADLINE_MS = 6_000L
        private const val TAG = "TriTV-Lines"
    }
}
