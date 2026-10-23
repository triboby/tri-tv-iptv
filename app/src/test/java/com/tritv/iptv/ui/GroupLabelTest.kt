package com.tritv.iptv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupLabelTest {
    @Test
    fun spacesLeadingEmojiFromName() {
        assertEquals("📺 央视频道", groupLabel("📺央视频道"))
        assertEquals("🇭🇰 港澳台", groupLabel("🇭🇰港澳台"))
        assertEquals("❤️ 收藏", groupLabel("❤️收藏"))
    }

    @Test
    fun leavesPlainOrAlreadySpacedNamesAlone() {
        assertEquals("央视", groupLabel("央视"))
        assertEquals("📡 卫视频道", groupLabel("📡 卫视频道"))
        assertEquals("📡", groupLabel("📡"))
        assertEquals("", groupLabel(""))
    }
}
