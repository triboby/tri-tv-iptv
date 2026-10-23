package com.tritv.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteListTest {

    @Test
    fun togglesAtTheEnd() {
        assertEquals(listOf("a", "b"), FavoriteList.toggle(listOf("a"), "b"))
        assertEquals(listOf("b"), FavoriteList.toggle(listOf("a", "b"), "a"))
    }

    @Test
    fun movesWithinBounds() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), FavoriteList.move(list, "b", -1))
        assertEquals(listOf("a", "c", "b"), FavoriteList.move(list, "b", 1))
        assertEquals(list, FavoriteList.move(list, "a", -1))
        assertEquals(list, FavoriteList.move(list, "c", 1))
        assertEquals(list, FavoriteList.move(list, "x", 1))
    }

    @Test
    fun roundTripsThroughText() {
        val list = listOf("cctv1", "湖南卫视")
        assertEquals(list, FavoriteList.decode(FavoriteList.encode(list)))
        assertEquals(emptyList<String>(), FavoriteList.decode(null))
        assertEquals(emptyList<String>(), FavoriteList.decode(""))
    }
}
