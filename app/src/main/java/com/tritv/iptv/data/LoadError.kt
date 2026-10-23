package com.tritv.iptv.data

import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** The playlist server answered with a non-2xx status. */
class HttpStatusException(val code: Int) : Exception("HTTP $code")

/** A short Chinese explanation of why loading the playlist failed, for the source page. */
fun loadErrorMessage(e: Throwable): String = when (e) {
    is MalformedURLException -> "地址格式不对，请以 http:// 或 https:// 开头"
    is UnknownHostException -> "找不到服务器，请检查地址是否写对"
    is ConnectException, is NoRouteToHostException -> "连不上服务器，请确认服务已开启，且电视和它在同一网络"
    is SocketTimeoutException -> "连接超时，服务器没有响应"
    is SSLException -> "HTTPS 安全连接失败，可以试试 http:// 地址"
    is HttpStatusException -> when (e.code) {
        404 -> "服务器上没有这个播放列表（HTTP 404）"
        401, 403 -> "服务器拒绝访问（HTTP ${e.code}）"
        else -> "服务器返回错误（HTTP ${e.code}）"
    }
    is FileNotFoundException -> "服务器上没有这个播放列表"
    else -> "加载失败，请检查地址"
}
