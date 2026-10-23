package com.tritv.iptv.player

import androidx.media3.common.MimeTypes
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
