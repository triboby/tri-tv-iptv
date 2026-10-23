package com.tritv.iptv.data

/**
 * Playlists name channels bare ("CCTV-10"), which says nothing about what's on. This maps the
 * well-known numbered and Hong Kong/Macau channels to a short category ("科教"). Provincial
 * satellite channels ("湖南卫视") and names that already say it ("凤凰资讯") are left out.
 */
object ChannelCategory {

    private val separators = Regex("""[\s_\-]+""")

    private val categories = mapOf(
        "cctv1" to "综合",
        "cctv2" to "财经",
        "cctv3" to "综艺",
        "cctv4" to "中文国际",
        "cctv5" to "体育",
        "cctv5+" to "体育赛事",
        "cctv6" to "电影",
        "cctv7" to "国防军事",
        "cctv8" to "电视剧",
        "cctv9" to "纪录",
        "cctv10" to "科教",
        "cctv11" to "戏曲",
        "cctv12" to "社会与法",
        "cctv13" to "新闻",
        "cctv14" to "少儿",
        "cctv15" to "音乐",
        "cctv16" to "奥林匹克",
        "cctv17" to "农业农村",
        "cetv1" to "综合教育",
        "cetv4" to "职业教育",
        "翡翠台" to "TVB 综合",
        "tvbplus" to "TVB 综合",
        "tvb星河" to "TVB 港剧",
        "viutv" to "综合娱乐",
        "hoytv" to "综合",
        "凤凰中文" to "综合",
        "凤凰香港" to "香港本地",
        "澳视澳门" to "澳门本地",
        "广东珠江" to "粤语",
        "广州竞赛" to "体育",
    )

    /** The category for [name], or null if unknown or the name already contains it. */
    fun of(name: String): String? {
        var key = name.replace(separators, "").lowercase()
        // "CCTV-1HD", "CCTV-5+ 高清" and similar.
        key = key.removeSuffix("高清").removeSuffix("hd").removeSuffix("4k").removeSuffix("超清")
        val category = categories[key] ?: return null
        return category.takeUnless { it.replace(" ", "") in name.replace(" ", "") }
    }

    /** "CCTV-10 科教" when there is a category, else the name alone. */
    fun label(name: String): String = of(name)?.let { "$name $it" } ?: name
}
