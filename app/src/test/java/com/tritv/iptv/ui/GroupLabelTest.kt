package com.tritv.iptv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupLabelTest {
    @Test
    fun splitsLeadingEmojiFromName() {
        assertEquals(GroupLabel("📺", "央视频道"), groupLabel("📺央视频道"))
        assertEquals(GroupLabel("🇭🇰", "港澳台"), groupLabel("🇭🇰港澳台"))
        assertEquals(GroupLabel("❤️", "收藏"), groupLabel("❤️收藏"))
        assertEquals(GroupLabel("📡", "卫视频道"), groupLabel("📡 卫视频道"))
    }

    @Test
    fun keepsPlainOrIconOnlyNamesWhole() {
        assertEquals(GroupLabel(null, "央视"), groupLabel("央视"))
        assertEquals(GroupLabel(null, "📡"), groupLabel("📡"))
        assertEquals(GroupLabel(null, ""), groupLabel(""))
    }
}
