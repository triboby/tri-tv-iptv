package com.tritv.iptv.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideUrlTest {

    @Test
    fun movesLoopbackGuideToPlaylistHost() {
        assertEquals(
            "http://192.168.50.58:8000/epg/epg.gz",
            resolveGuideUrl("http://127.0.0.1:8000/epg/epg.gz", "http://192.168.50.58:8000/m3u"),
        )
        assertEquals(
            "http://tv.lan/e.xml?d=3",
            resolveGuideUrl("http://localhost/e.xml?d=3", "http://tv.lan:9000/list.m3u"),
        )
    }

    @Test
    fun leavesOtherAddressesAlone() {
        assertEquals("http://epg.example/e.xml", resolveGuideUrl("http://epg.example/e.xml", "http://192.168.1.2/m3u"))
        assertEquals("http://127.0.0.1/e.xml", resolveGuideUrl("http://127.0.0.1/e.xml", "http://127.0.0.1/m3u"))
        assertEquals("not a url", resolveGuideUrl("not a url", "http://192.168.1.2/m3u"))
    }
}
