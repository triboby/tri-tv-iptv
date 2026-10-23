package com.tritv.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelMergerTest {

    private fun entry(name: String, url: String, group: String? = "央视", tvgId: String? = null, logo: String? = null) =
        PlaylistEntry(name = name, url = url, group = group, tvgId = tvgId, logo = logo)

    @Test
    fun mergesRepeatedChannelsIntoSources() {
        val channels = ChannelMerger.merge(
            listOf(
                entry("CCTV-1", "http://a/1.m3u8", tvgId = "CCTV-1"),
                entry("CCTV-2", "http://a/2.m3u8", tvgId = "CCTV-2"),
                entry("CCTV-1", "http://b/1.m3u8", tvgId = "CCTV-1", logo = "http://logo/1.png"),
                entry("CCTV-1", "http://a/1.m3u8", tvgId = "CCTV-1"),
            ),
        )

        assertEquals(listOf("CCTV-1", "CCTV-2"), channels.map { it.name })
        assertEquals(listOf("http://a/1.m3u8", "http://b/1.m3u8"), channels[0].sources.map { it.url })
        assertEquals("http://logo/1.png", channels[0].logo)
    }

    @Test
    fun matchesNamesIgnoringCaseAndSeparators() {
        val channels = ChannelMerger.merge(
            listOf(entry("CCTV-1", "http://a/1"), entry("cctv 1", "http://b/1"), entry("CCTV_1", "http://c/1")),
        )
        assertEquals(1, channels.size)
        assertEquals(3, channels[0].sources.size)
    }

    @Test
    fun keepsSameChannelInDifferentGroupsApart() {
        val channels = ChannelMerger.merge(
            listOf(entry("CCTV-1", "http://a/1", group = "央视"), entry("CCTV-1", "http://b/1", group = "收藏")),
        )
        assertEquals(listOf("央视", "收藏"), channels.map { it.group })
    }

    @Test
    fun dropsUpdateTimeEntry() {
        val channels = ChannelMerger.merge(
            listOf(entry("2026-10-23 13:31:43", "http://a/1", group = "🕘️更新时间"), entry("CCTV-1", "http://a/1")),
        )
        assertEquals(listOf("CCTV-1"), channels.map { it.name })
    }
}
