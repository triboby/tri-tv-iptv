package com.tritv.iptv.player

import androidx.media3.common.MimeTypes
import com.tritv.iptv.data.LineStat
import com.tritv.iptv.data.PlaylistEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPlanTest {

    @Test
    fun picksFormatsFromExtension() {
        assertEquals(listOf(MimeTypes.APPLICATION_M3U8), mimeCandidates("http://a/live/index.m3u8?token=1"))
        assertEquals(listOf(MimeTypes.APPLICATION_MPD), mimeCandidates("http://a/manifest.mpd"))
        assertEquals(listOf<String?>(null), mimeCandidates("http://a/stream.flv"))
    }

    @Test
    fun unknownUrlsTryHlsThenSniff() {
        val expected = listOf(MimeTypes.APPLICATION_M3U8, null)
        assertEquals(expected, mimeCandidates("http://a/phenix.php?id=fhzx"))
        assertEquals(expected, mimeCandidates("https://a/2b657a18.ctv"))
        assertEquals(expected, mimeCandidates("http://r.example.com/sE5vO"))
        assertEquals(expected, mimeCandidates("http://a.example:8080/"))
    }

    @Test
    fun attemptsWalkSourcesInOrder() {
        val attempts = playbackAttempts(
            listOf(PlaylistEntry("A", "http://a/1.m3u8"), PlaylistEntry("A", "http://b/live.php")),
        )
        assertEquals(
            listOf(
                Attempt(0, MimeTypes.APPLICATION_M3U8),
                Attempt(1, MimeTypes.APPLICATION_M3U8),
                Attempt(1, null),
            ),
            attempts,
        )
    }

    @Test
    fun rtspUrlsUseRtsp() {
        assertEquals(listOf(MimeTypes.APPLICATION_RTSP), mimeCandidates("rtsp://10.0.0.1:554/live/ch1"))
    }

    @Test
    fun failuresWalkForwardThenReturnToSilentLine() {
        assertEquals(1, nextAttempt(current = 0, count = 3, silentLine = null))
        assertEquals(2, nextAttempt(current = 1, count = 3, silentLine = 0))
        assertEquals(0, nextAttempt(current = 2, count = 3, silentLine = 0))
        assertNull(nextAttempt(current = 2, count = 3, silentLine = null))
        assertNull(nextAttempt(current = 0, count = 1, silentLine = 0))
    }
}

class ProbeSwitchTest {

    private val attempts = listOf(Attempt(0, null), Attempt(1, null), Attempt(2, null))
    private val urls = listOf("a", "b", "c")

    @Test
    fun leavesDeadLineForFastestWorkingOne() {
        val stats = mapOf(
            "a" to LineStat(failAt = 1_500),
            "b" to LineStat(latencyMs = 900, okAt = 1_200),
            "c" to LineStat(latencyMs = 100, okAt = 1_200),
        )
        assertEquals(2, probeSwitch(attempts, urls, stats, current = 0, startedAt = 1_000, now = 1_600))
    }

    @Test
    fun deadLineWithNothingKnownWorkingMovesOn() {
        val stats = mapOf("a" to LineStat(failAt = 1_500), "b" to LineStat(failAt = 1_400))
        assertEquals(2, probeSwitch(attempts, urls, stats, current = 0, startedAt = 1_000, now = 1_600))
    }

    @Test
    fun failureFromBeforeTheAttemptIsIgnored() {
        val stats = mapOf("a" to LineStat(failAt = 900))
        assertNull(probeSwitch(attempts, urls, stats, current = 0, startedAt = 1_000, now = 1_600))
    }

    @Test
    fun slowUnknownLineGivesWayAfterGracePeriod() {
        val stats = mapOf("b" to LineStat(latencyMs = 200, okAt = 1_200))
        assertNull(probeSwitch(attempts, urls, stats, current = 0, startedAt = 1_000, now = 1_000 + SLOW_START_MS - 1))
        assertEquals(1, probeSwitch(attempts, urls, stats, current = 0, startedAt = 1_000, now = 1_000 + SLOW_START_MS))
    }

    @Test
    fun workingLineIsLeftToLoad() {
        val stats = mapOf("a" to LineStat(latencyMs = 500, okAt = 1_100), "b" to LineStat(latencyMs = 50, okAt = 1_100))
        assertNull(probeSwitch(attempts, urls, stats, current = 0, startedAt = 1_000, now = 10_000))
    }
}
