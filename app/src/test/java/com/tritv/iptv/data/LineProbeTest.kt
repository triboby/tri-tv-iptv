package com.tritv.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineProbeTest {

    @Test
    fun masterPlaylistFollowsFirstVariant() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=2000000
            hd/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=800000
            sd/index.m3u8
        """.trimIndent()
        assertEquals("hd/index.m3u8", LineProbe.nextUri(master))
    }

    @Test
    fun mediaPlaylistFollowsNewestSegment() {
        val media = "#EXTM3U\r\n#EXT-X-TARGETDURATION:6\r\n#EXTINF:6,\r\nseg100.ts\r\n#EXTINF:6,\r\nhttp://cdn/seg101.ts\r\n"
        assertEquals("http://cdn/seg101.ts", LineProbe.nextUri(media))
    }

    @Test
    fun emptyPlaylistHasNoNext() {
        assertNull(LineProbe.nextUri("#EXTM3U\n#EXT-X-TARGETDURATION:6\n"))
    }

    @Test
    fun recognisesPlaylists() {
        assertTrue(LineProbe.isPlaylist("﻿#EXTM3U\n"))
        assertTrue(LineProbe.isPlaylist("\n #EXTM3U"))
        assertFalse(LineProbe.isPlaylist("<html>"))
    }
}
