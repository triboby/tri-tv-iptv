package com.tritv.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelCategoryTest {

    @Test
    fun namesNumberedChannels() {
        assertEquals("科教", ChannelCategory.of("CCTV-10"))
        assertEquals("体育赛事", ChannelCategory.of("CCTV-5+"))
        assertEquals("综合", ChannelCategory.of("cctv 1 HD"))
        assertEquals("TVB 综合", ChannelCategory.of("翡翠台"))
    }

    @Test
    fun skipsUnknownAndSelfDescribingNames() {
        assertNull(ChannelCategory.of("湖南卫视"))
        assertNull(ChannelCategory.of("CCTV-1 综合"))
        assertNull(ChannelCategory.of("CCTV-100"))
    }

    @Test
    fun labelsAppendTheCategory() {
        assertEquals("CCTV-10 科教", ChannelCategory.label("CCTV-10"))
        assertEquals("湖南卫视", ChannelCategory.label("湖南卫视"))
    }
}
