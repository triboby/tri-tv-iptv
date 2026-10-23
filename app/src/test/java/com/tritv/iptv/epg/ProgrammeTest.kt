package com.tritv.iptv.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgrammeTest {

    private val list = listOf(Programme(10, 20, "a"), Programme(20, 30, "b"), Programme(40, 50, "c"))

    @Test
    fun findsNowAndNext() {
        assertEquals(list[0] to list[1], list.nowAndNext(15))
        assertEquals(list[1] to list[2], list.nowAndNext(20))
        assertEquals(null to list[2], list.nowAndNext(35))
        assertEquals(null to list[0], list.nowAndNext(0))
        assertEquals(null to null, list.nowAndNext(60))
    }

    @Test
    fun findsProgrammeAtTime() {
        assertEquals("b", list.at(25)?.title)
        assertEquals("c", list.at(35)?.title)
        assertEquals("c", list.at(99)?.title)
        assertNull(emptyList<Programme>().at(1))
    }

    @Test
    fun progressIsClamped() {
        assertEquals(0.5f, list[0].progress(15), 0f)
        assertEquals(0f, list[0].progress(0), 0f)
        assertEquals(1f, list[0].progress(99), 0f)
    }
}
