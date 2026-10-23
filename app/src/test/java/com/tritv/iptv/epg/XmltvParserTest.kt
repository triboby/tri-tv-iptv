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
        val guide = parse(
            """
            <tv>
              <programme start="20261023203000 +0800" stop="20261023212300 +0800" channel="X"><title>A</title></programme>
              <programme start="20261023211000 +0800" stop="20261023221700 +0800" channel="X"><title>B</title></programme>
              <programme start="20261023212300 +0800" stop="20261023221700 +0800" channel="X"><title>inside B</title></programme>
              <programme start="20261023221700 +0800" stop="20261023221730 +0800" channel="X"><title>30 seconds</title></programme>
              <programme start="20261023221730 +0800" stop="20261023230000 +0800" channel="X"><title>C</title></programme>
            </tv>
            """.trimIndent(),
        )
        assertEquals(listOf("A", "B", "C"), guide.getValue("X").map { it.title })
        assertEquals(XmltvParser.parseTime("20261023211000 +0800"), guide.getValue("X")[0].stop)
    }

    @Test
    fun prefersTheLocalOffsetWhenAChannelIsAlsoListedInUtc() {
        // CCTV-3 as served on 2026-10-23: a second source lists Beijing time labelled +0000.
        val guide = parse(
            """
            <tv>
              <channel id="CCTV-3"><display-name lang="zh">CCTV-3</display-name></channel>
              <programme channel="CCTV-3" start="20261023112900 +0000" stop="20261023130000 +0000"><title>越战越勇-2026-33</title></programme>
              <programme channel="CCTV-3" start="20261023112945 +0000" stop="20261023130000 +0000"><title>越战越勇-2026-33</title></programme>
              <programme channel="CCTV-3" start="20261023130000 +0000" stop="20261023132300 +0000"><title>文化十分-2026-214</title></programme>
              <programme channel="CCTV-3" start="20261023130000 +0000" stop="20261023132340 +0000"><title>文化十分-2026-214</title></programme>
              <programme channel="CCTV-3" start="20261023132300 +0000" stop="20261023150800 +0000"><title>综艺喜乐汇</title></programme>
              <programme channel="CCTV-3" start="20261023132340 +0000" stop="20261023150845 +0000"><title>综艺喜乐汇-2025-1422 文艺专题</title></programme>
              <programme channel="CCTV-3" start="20261023150800 +0000" stop="20261023151800 +0000"><title>连环话-千里走单骑</title></programme>
              <programme channel="CCTV-3" start="20261023150845 +0000" stop="20261023151845 +0000"><title>连环话-千里走单骑</title></programme>
              <programme channel="CCTV-3" start="20261023151800 +0000" stop="20261023152700 +0000"><title>综艺喜乐汇</title></programme>
              <programme channel="CCTV-3" start="20261023151845 +0000" stop="20261023152735 +0000"><title>综艺喜乐汇-2026-1128 岁月长情</title></programme>
              <programme channel="CCTV-3" start="20261023152700 +0000" stop="20261023165900 +0000"><title>全民畅舞-26</title></programme>
              <programme channel="CCTV-3" start="20261023152735 +0000" stop="20261023165945 +0000"><title>全民畅舞-26</title></programme>
              <programme channel="CCTV-3" start="20261023193000 +0800" stop="20261023201100 +0800"><title>纪念中国工农红军长征胜利90周年电视文艺晚会-上</title></programme>
              <programme channel="CCTV-3" start="20261023201100 +0800" stop="20261023204700 +0800"><title>纪念中国工农红军长征胜利90周年电视文艺晚会-中</title></programme>
              <programme channel="CCTV-3" start="20261023204700 +0800" stop="20261023211030 +0800"><title>纪念中国工农红军长征胜利90周年电视文艺晚会-下</title></programme>
              <programme channel="CCTV-3" start="20261023211030 +0800" stop="20261023221700 +0800"><title>新花路放—最美的心愿之旅-10</title></programme>
              <programme channel="CCTV-3" start="20261023221700 +0800" stop="20261023224945 +0800"><title>综艺喜乐汇-2026-1381 多彩少年 8</title></programme>
              <programme channel="CCTV-3" start="20261023224945 +0800" stop="20261023233545 +0800"><title>综艺喜乐汇-2026-1168 以法之名26/36</title></programme>
            </tv>
            """.trimIndent(),
        )
        val list = guide.getValue("CCTV-3")
        val t = { s: String -> XmltvParser.parseTime("20261023$s +0800")!! }
        assertEquals(
            listOf("193000", "201100", "204700", "211030", "221700", "224945"),
            list.map { java.text.SimpleDateFormat("HHmmss").apply { timeZone = java.util.TimeZone.getTimeZone("GMT+8") }.format(it.start) },
        )
        val now = list.first { it.isOn(t("213800")) }
        assertEquals("新花路放—最美的心愿之旅-10", now.title)
        assertEquals(t("211030"), now.start)
        assertEquals(t("221700"), now.stop)
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
