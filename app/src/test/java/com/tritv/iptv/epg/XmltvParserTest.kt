package com.tritv.iptv.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmltvParserTest {

    private val day = XmltvParser.parseTime("20261023000000 +0800")!!

    private fun parse(xml: String, wanted: (String, List<String>) -> Boolean = { _, _ -> true }) =
        XmltvParser.parse(xml.byteInputStream(), from = day, to = day + 86_400_000L, wanted = wanted)

    @Test
    fun parsesTimesWithOffsets() {
        // 2026-10-23 00:00 in UTC+8 is 2026-10-22 16:00 UTC.
        assertEquals(1_792_684_800_000L, XmltvParser.parseTime("20261023000000 +0800"))
        assertEquals(1_792_684_800_000L, XmltvParser.parseTime("20261022160000 +0000"))
        assertEquals(1_792_684_800_000L, XmltvParser.parseTime("202610221600 +00:00"))
        assertEquals(1_792_684_800_000L, XmltvParser.parseTime("20261023000000"))
        assertEquals(1_792_684_800_000L, XmltvParser.parseTime("20261022110000 -0500"))
        assertNull(XmltvParser.parseTime("2026-10-23"))
        assertNull(XmltvParser.parseTime("20261323000000"))
    }

    @Test
    fun keepsProgrammesInWindowSortedPerChannel() {
        val guide = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="CCTV1"><display-name lang="zh">CCTV-1 综合</display-name></channel>
              <programme start="20261023200000 +0800" stop="20261023210000 +0800" channel="CCTV1">
                <title lang="zh">焦点访谈 &amp; 评论</title><desc>今日要闻</desc>
              </programme>
              <programme start="20261023190000 +0800" stop="20261023200000 +0800" channel="CCTV1">
                <title>新闻联播</title><title lang="en">News</title>
              </programme>
              <programme start="20261022230000 +0800" stop="20261022233000 +0800" channel="CCTV1">
                <title>昨天</title>
              </programme>
              <programme start="20261023233000 +0800" stop="20261024003000 +0800" channel="CCTV1">
                <title>跨零点</title>
              </programme>
            </tv>
            """.trimIndent(),
        )
        val list = guide.getValue("CCTV1")
        assertEquals(listOf("新闻联播", "焦点访谈 & 评论", "跨零点"), list.map { it.title })
        assertEquals("今日要闻", list[1].desc)
        assertNull(list[0].desc)
    }

    @Test
    fun asksWhichChannelsToKeepWithTheirDisplayNames() {
        val asked = mutableListOf<Pair<String, List<String>>>()
        val guide = parse(
            """
            <tv>
              <channel id="1"><display-name>CCTV-1</display-name><display-name>CCTV1</display-name></channel>
              <channel id="2"><display-name>Other</display-name></channel>
              <programme start="20261023100000 +0800" stop="20261023110000 +0800" channel="1"><title>A</title></programme>
              <programme start="20261023100000 +0800" stop="20261023110000 +0800" channel="2"><title>B</title></programme>
              <programme start="20261023110000 +0800" stop="20261023120000 +0800" channel="2"><title>C</title></programme>
            </tv>
            """.trimIndent(),
        ) { id, names -> asked += id to names; id == "1" }
        assertEquals(listOf("1" to listOf("CCTV-1", "CCTV1"), "2" to listOf("Other")), asked)
        assertEquals(setOf("1"), guide.keys)
    }

    @Test
    fun resolvesOverlappingProgrammes() {
        // As CCTV-3's guide had it: 新花路放 aired 21:10–22:17, then 综艺喜乐汇 from 22:17.
        val guide = parse(
            """
            <tv>
              <programme start="20261023203000 +0800" stop="20261023212300 +0800" channel="CCTV3"><title>开门大吉</title></programme>
              <programme start="20261023211000 +0800" stop="20261023221700 +0800" channel="CCTV3"><title>新花路放</title></programme>
              <programme start="20261023212300 +0800" stop="20261023221700 +0800" channel="CCTV3"><title>综艺喜乐汇</title></programme>
              <programme start="20261023221700 +0800" stop="20261023230000 +0800" channel="CCTV3"><title>综艺喜乐汇</title></programme>
            </tv>
            """.trimIndent(),
        )
        val list = guide.getValue("CCTV3")
        val t = { s: String -> XmltvParser.parseTime("20261023${s}00 +0800")!! }
        assertEquals(
            listOf(
                Programme(t("2030"), t("2110"), "开门大吉"),
                Programme(t("2110"), t("2217"), "新花路放"),
                Programme(t("2217"), t("2300"), "综艺喜乐汇"),
            ),
            list,
        )
    }

    @Test
    fun skipsProgrammesWithoutTitleOrValidTimes() {
        val guide = parse(
            """
            <tv>
              <programme start="20261023100000 +0800" stop="20261023110000 +0800" channel="x"></programme>
              <programme start="bad" stop="20261023110000 +0800" channel="x"><title>A</title></programme>
              <programme start="20261023110000 +0800" stop="20261023100000 +0800" channel="x"><title>B</title></programme>
              <programme start="20261023120000 +0800" stop="20261023130000 +0800" channel="x"><title>C</title></programme>
            </tv>
            """.trimIndent(),
        )
        assertEquals(listOf("C"), guide.getValue("x").map { it.title })
    }
}
