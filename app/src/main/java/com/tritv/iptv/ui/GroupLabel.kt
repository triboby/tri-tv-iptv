package com.tritv.iptv.ui

/**
 * Group titles often start with an emoji ("📺央视频道", "🇭🇰港澳台"). Puts an en space between that
 * leading symbol run and the name so the two don't render glued together.
 */
fun groupLabel(group: String): String {
    var i = 0
    while (i < group.length) {
        val cp = group.codePointAt(i)
        if (Character.isLetterOrDigit(cp) || Character.isWhitespace(cp)) break
        i += Character.charCount(cp)
    }
    if (i == 0 || i >= group.length || Character.isWhitespace(group.codePointAt(i))) return group
    return group.substring(0, i) + " " + group.substring(i)
}
