package com.tritv.iptv.data

/** Operations on the ordered list of favourite [Channel.stableKey]s. */
object FavoriteList {

    /** Adds [key] at the end, or removes it if already there. */
    fun toggle(list: List<String>, key: String): List<String> =
        if (key in list) list - key else list + key

    /** Moves [key] one place up (-1) or down (+1); unchanged at either end. */
    fun move(list: List<String>, key: String, delta: Int): List<String> {
        val from = list.indexOf(key)
        val to = from + delta
        if (from < 0 || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun encode(list: List<String>): String = list.joinToString("\n")

    fun decode(text: String?): List<String> =
        text.orEmpty().split('\n').filter { it.isNotEmpty() }.distinct()
}
