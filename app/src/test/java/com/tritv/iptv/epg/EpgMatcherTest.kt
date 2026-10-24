package com.tritv.iptv.epg

import com.tritv.iptv.data.Channel
import com.tritv.iptv.data.PlaylistEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgMatcherTest {

    private fun channel(name: String, tvgId: String? = null, tvgName: String? = null) =
        Channel(name, "央视", emptyList(), listOf(PlaylistEntry(name, "http://x/$name", tvgId = tvgId, tvgName = tvgName)))

    private fun prog(title: String) = listOf(Programme(0, 1, title))

    @Test
    fun normalizesWidthCaseSeparatorsAndQualityTags() {
        assertEquals("cctv1", EpgMatcher.normalize("CCTV-1"))
        assertEquals("cctv1", EpgMatcher.normalize("ＣＣＴＶ１"))
        assertEquals("cctv1", EpgMatcher.normalize("cctv 1 HD"))
        assertEquals("湖南卫视", EpgMatcher.normalize("湖南卫视高清"))
        assertEquals("cctv4k", EpgMatcher.normalize("CCTV-4K"))
    }

    @Test
    fun matchesByTvgIdThenNames() {
        val channels = listOf(
            channel("CCTV-1 综合", tvgId = "cctv1"),
            channel("湖南卫视 HD"),
            channel("CCTV-5 体育", tvgName = "CCTV5"),
            channel("Nothing"),
        )
        val guide = mapOf("CCTV1" to prog("a"), "hunan" to prog("b"), "5" to prog("c"))
        val names = mapOf("hunan" to listOf("湖南卫视"), "5" to listOf("CCTV5 体育"))

        val m = EpgMatcher(channels).match(guide, names)

        assertEquals("a", m.getValue(channels[0].stableKey).single().title)
        assertEquals("b", m.getValue(channels[1].stableKey).single().title)
        assertEquals("c", m.getValue(channels[2].stableKey).single().title)
        assertFalse(channels[3].stableKey in m)
    }

    @Test
    fun wantsOnlyChannelsThePlaylistHas() {
        val matcher = EpgMatcher(listOf(channel("CCTV-1 综合", tvgId = "cctv1"), channel("湖南卫视")))
        assertTrue(matcher.wants("CCTV1", emptyList()))
        assertTrue(matcher.wants("123", listOf("湖南卫视")))
        assertTrue(matcher.wants("124", listOf("CCTV-1 综合频道")))
        assertFalse(matcher.wants("999", listOf("浙江卫视")))
    }
}
