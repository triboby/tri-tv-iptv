package com.tritv.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    @Test
    fun parsesExtInfAttributesAndName() {
        val text = """
            #EXTM3U x-tvg-url="http://epg.example/e.xml"
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV1" tvg-logo="http://logo/1.png" group-title="央视",CCTV-1 综合
            http://host/live/cctv1.m3u8
        """.trimIndent()

        val ch = M3uParser.parse(text).single()
        assertEquals("CCTV-1 综合", ch.name)
        assertEquals("http://host/live/cctv1.m3u8", ch.url)
        assertEquals("央视", ch.group)
        assertEquals("http://logo/1.png", ch.logo)
        assertEquals("cctv1", ch.tvgId)
    }

    @Test
    fun handlesCommaInsideQuotedAttribute() {
        val text = """
            #EXTM3U
            #EXTINF:-1 group-title="News, World",BBC World
            http://host/bbc
        """.trimIndent()

        val ch = M3uParser.parse(text).single()
        assertEquals("BBC World", ch.name)
        assertEquals("News, World", ch.group)
    }

    @Test
    fun readsExtGrpAndVlcOptions() {
        val text = "#EXTM3U\r\n" +
            "#EXTINF:-1,Local\r\n" +
            "#EXTGRP:地方台\r\n" +
            "#EXTVLCOPT:http-user-agent=Mozilla/5.0\r\n" +
            "#EXTVLCOPT:http-referrer=http://ref/\r\n" +
            "http://host/local\r\n"

        val ch = M3uParser.parse(text).single()
        assertEquals("地方台", ch.group)
        assertEquals("Mozilla/5.0", ch.userAgent)
        assertEquals("http://ref/", ch.referrer)
    }

    @Test
    fun bareUrlsWithoutExtInfAreKept() {
        val channels = M3uParser.parse("http://a/one.m3u8\nhttp://a/two.mpd\n")
        assertEquals(listOf("one.m3u8", "two.mpd"), channels.map { it.name })
        assertNull(channels[0].group)
    }

    @Test
    fun fallsBackToTvgNameWhenTitleMissing() {
        val ch = M3uParser.parse("#EXTINF:-1 tvg-name=\"Fallback\",\nhttp://x/y").single()
        assertEquals("Fallback", ch.name)
    }
}
