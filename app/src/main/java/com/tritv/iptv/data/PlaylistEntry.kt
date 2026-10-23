package com.tritv.iptv.data

data class PlaylistEntry(
    val name: String,
    val url: String,
    val group: String? = null,
    val logo: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
)
