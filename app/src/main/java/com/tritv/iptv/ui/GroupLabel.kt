package com.tritv.iptv.ui

/** A group title split into its leading emoji/symbol (if any) and the name after it. */
data class GroupLabel(val icon: String?, val name: String)

/**
 * Group titles often start with an emoji ("📺央视频道", "🇭🇰港澳台"). Splits that leading symbol run
 * off so the UI can put it in a fixed-size, centred icon slot with a set gap before the name.
 */
fun groupLabel(group: String): GroupLabel {
    var i = 0
    while (i < group.length) {
        val cp = group.codePointAt(i)
        if (Character.isLetterOrDigit(cp) || Character.isWhitespace(cp)) break
        i += Character.charCount(cp)
    }
    val name = group.substring(i).trim()
    if (i == 0 || name.isEmpty()) return GroupLabel(null, group.trim())
    return GroupLabel(group.substring(0, i), name)
}
