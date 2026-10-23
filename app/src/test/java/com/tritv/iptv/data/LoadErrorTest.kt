package com.tritv.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.URL
import java.net.UnknownHostException

class LoadErrorTest {
    @Test
    fun explainsCommonFailuresInChinese() {
        val badScheme = runCatching { URL("qhttp://100.67.190.103:8000/m3u") }.exceptionOrNull()!!
        assertEquals(true, badScheme is MalformedURLException)
        assertEquals("地址格式不对，请以 http:// 或 https:// 开头", loadErrorMessage(badScheme))
        assertEquals("连不上服务器，请确认服务已开启，且电视和它在同一网络", loadErrorMessage(ConnectException("Failed to connect")))
        assertEquals("找不到服务器，请检查地址是否写对", loadErrorMessage(UnknownHostException("x")))
        assertEquals("服务器上没有这个播放列表（HTTP 404）", loadErrorMessage(HttpStatusException(404)))
        assertEquals("服务器返回错误（HTTP 500）", loadErrorMessage(HttpStatusException(500)))
        assertEquals("加载失败，请检查地址", loadErrorMessage(IllegalStateException("?")))
    }
}
