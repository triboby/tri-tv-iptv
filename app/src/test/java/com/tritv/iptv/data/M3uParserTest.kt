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

    @Test
    fun parsesAggregatorStylePlaylist() {
        // Trimmed shape of a real aggregated source: emoji groups, a fake
        // "update time" entry, repeated channels, catchup attrs, UA options.
        val text = """
            #EXTM3U x-tvg-url="http://127.0.0.1:8000/epg/epg.gz"
            #EXTINF:-1 tvg-id="CCTV-1" tvg-name="CCTV-1" tvg-logo="https://logo.example/CCTV-1.png" group-title="🕘️更新时间",2026-10-23 13:31:43
            http://a.example/hls/cctv1hd.m3u8
            #EXTINF:-1 tvg-id="CCTV-1" tvg-name="CCTV-1" tvg-logo="https://logo.example/CCTV1.png" group-title="📺央视频道",CCTV-1
            http://a.example/hls/cctv1hd.m3u8
            #EXTINF:-1 tvg-id="CCTV-1" tvg-name="CCTV-1" tvg-logo="https://logo.example/CCTV1.png" group-title="📺央视频道",CCTV-1
            http://b.example/PLTV/88888888/224/3221226559/index.m3u8
            #EXTINF:-1 tvg-id="东方卫视" tvg-name="东方卫视" tvg-logo="https://logo.example/东方卫视.png" group-title="📡卫视频道",东方卫视
            #EXTVLCOPT:http-user-agent=AptvPlayer-UA
            http://c.example/155/3/video.m3u8
            #EXTINF:-1 tvg-id="凤凰资讯" tvg-name="凤凰资讯" tvg-logo="https://logo.example/fhzx.png" group-title="🇭🇰港澳台" catchup="append" catchup-source="?playseek=${'$'}{(b)yyyyMMddHHmmss}-${'$'}{(e)yyyyMMddHHmmss}",凤凰资讯
            #EXTVLCOPT:http-user-agent=iPhone
            http://d.example/phenix.php?id=fhzx
            #EXTINF:-1 tvg-id="翡翠台" tvg-name="翡翠台" tvg-logo="https://logo.example/tvb.png" group-title="🇭🇰港澳台",翡翠台
            https://e.example/2b657a181daa345025199e7d.ctv
        """.trimIndent()

        val channels = M3uParser.parse(text)
        assertEquals(6, channels.size)
        assertEquals("2026-10-23 13:31:43", channels[0].name)
        assertEquals("🕘️更新时间", channels[0].group)
        assertEquals(listOf("CCTV-1", "CCTV-1"), channels.subList(1, 3).map { it.name })
        assertEquals("📺央视频道", channels[1].group)

        val dongfang = channels[3]
        assertEquals("东方卫视", dongfang.name)
        assertEquals("AptvPlayer-UA", dongfang.userAgent)

        val fenghuang = channels[4]
        assertEquals("凤凰资讯", fenghuang.name)
        assertEquals("🇭🇰港澳台", fenghuang.group)
        assertEquals("iPhone", fenghuang.userAgent)
        assertEquals("http://d.example/phenix.php?id=fhzx", fenghuang.url)

        assertNull(channels[5].userAgent)
    }

    @Test
    fun readsGuideUrlFromHeader() {
        assertEquals("http://epg/a.xml", M3uParser.guideUrl("#EXTM3U x-tvg-url=\"http://epg/a.xml, http://epg/b.xml\"\n#EXTINF:-1,A\nhttp://a"))
        assertEquals("http://epg/c.xml.gz", M3uParser.guideUrl("\uFEFF#EXTM3U url-tvg=\"http://epg/c.xml.gz\""))
        assertNull(M3uParser.guideUrl("#EXTM3U\n#EXTINF:-1 x-tvg-url=\"http://no\",A\nhttp://a"))
    }

    @Test
    fun keepsTvgName() {
        val ch = M3uParser.parse("#EXTINF:-1 tvg-name=\"CCTV5\",CCTV-5 体育\nhttp://a").single()
        assertEquals("CCTV5", ch.tvgName)
    }
}
