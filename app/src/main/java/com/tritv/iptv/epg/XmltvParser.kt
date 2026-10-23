package com.tritv.iptv.epg

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/** Programmes by XMLTV channel id, each list sorted by start time. */
typealias Guide = Map<String, List<Programme>>

/**
 * Streams an XMLTV file (often tens of MB) with SAX, keeping only programmes that overlap
 * [from, to) and belong to channels [wanted] accepts. `wanted` gets the channel id and the
 * `<display-name>`s seen for it so far (XMLTV lists `<channel>` elements before programmes).
 *
 * ```
 * <channel id="CCTV1"><display-name>CCTV-1 综合</display-name></channel>
 * <programme start="20261023193000 +0800" stop="20261023200000 +0800" channel="CCTV1">
 *   <title>新闻联播</title>
 * </programme>
 * ```
 */
object XmltvParser {

    /** Offset assumed when a time has none; Chinese guides are in UTC+8. */
    const val DEFAULT_OFFSET_MS = 8 * 3_600_000L

    fun parse(
        input: InputStream,
        from: Long,
        to: Long,
        defaultOffsetMs: Long = DEFAULT_OFFSET_MS,
        wanted: (id: String, displayNames: List<String>) -> Boolean = { _, _ -> true },
    ): Guide {
        val handler = Handler(from, to, defaultOffsetMs, wanted)
        val factory = SAXParserFactory.newInstance()
        // A guide never needs external entities; refusing them avoids XXE and network fetches.
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.newSAXParser().parse(InputSource(input), handler)
        return handler.result()
    }

    /**
     * Parses `20261023193000 +0800` (also without seconds or offset) to epoch milliseconds,
     * or null if malformed.
     */
    fun parseTime(value: String, defaultOffsetMs: Long = DEFAULT_OFFSET_MS): Long? {
        val s = value.trim()
        var n = 0
        while (n < s.length && s[n].isDigit()) n++
        if (n < 12) return null
        val digits = s.substring(0, n)
        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4, 6).toInt()
        val day = digits.substring(6, 8).toInt()
        val hour = digits.substring(8, 10).toInt()
        val minute = digits.substring(10, 12).toInt()
        val second = if (n >= 14) digits.substring(12, 14).toInt() else 0
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null

        val offset = Regex("""([+-])(\d{2}):?(\d{2})""").find(s, n)?.let {
            val sign = if (it.groupValues[1] == "-") -1 else 1
            sign * (it.groupValues[2].toLong() * 3_600_000 + it.groupValues[3].toLong() * 60_000)
        } ?: defaultOffsetMs
        val local = daysFromCivil(year, month, day) * 86_400_000L +
            hour * 3_600_000L + minute * 60_000L + second * 1_000L
        return local - offset
    }

    /** Days since 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's algorithm). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    private class Handler(
        val from: Long,
        val to: Long,
        val defaultOffsetMs: Long,
        val wanted: (String, List<String>) -> Boolean,
    ) : DefaultHandler() {
        val names = HashMap<String, MutableList<String>>()
        val decisions = HashMap<String, Boolean>()
        val programmes = HashMap<String, MutableList<Programme>>()

        val text = StringBuilder()
        var collecting = false
        var channelId: String? = null

        // Current <programme>, or null when outside one or skipping it.
        var progChannel: String? = null
        var progStart = 0L
        var progStop = 0L
        var title: String? = null
        var desc: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
            when (qName) {
                "channel" -> channelId = attrs.getValue("id")
                "display-name" -> if (channelId != null) startText()
                "programme" -> startProgramme(attrs)
                "title" -> if (progChannel != null && title == null) startText()
                "desc" -> if (progChannel != null && desc == null) startText()
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (qName) {
                "channel" -> channelId = null
                "display-name" -> channelId?.let { id ->
                    endText()?.let { names.getOrPut(id) { mutableListOf() } += it }
                }
                "title" -> if (collecting) title = endText()
                "desc" -> if (collecting) desc = endText()
                "programme" -> endProgramme()
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (collecting) text.append(ch, start, length)
        }

        private fun startText() {
            text.setLength(0)
            collecting = true
        }

        private fun endText(): String? {
            collecting = false
            return text.toString().trim().ifEmpty { null }
        }

        private fun startProgramme(attrs: Attributes) {
            progChannel = null
            title = null
            desc = null
            val id = attrs.getValue("channel") ?: return
            val start = attrs.getValue("start")?.let { parseTime(it, defaultOffsetMs) } ?: return
            val stop = attrs.getValue("stop")?.let { parseTime(it, defaultOffsetMs) } ?: return
            if (stop <= start || stop <= from || start >= to) return
            if (!decisions.getOrPut(id) { wanted(id, names[id].orEmpty()) }) return
            progChannel = id
            progStart = start
            progStop = stop
        }

        private fun endProgramme() {
            val id = progChannel ?: return
            progChannel = null
            val t = title ?: return
            programmes.getOrPut(id) { mutableListOf() } += Programme(progStart, progStop, t, desc)
        }

        fun result(): Guide = programmes.mapValues { (_, list) -> list.sortedBy { it.start }.distinctBy { it.start }.withoutOverlaps() }
    }
}
