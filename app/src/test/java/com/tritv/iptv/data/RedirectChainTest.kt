package com.tritv.iptv.data

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class RedirectChainTest {

    private lateinit var server: HttpServer
    private lateinit var base: String

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun redirect(path: String, to: String) = server.createContext(path) {
            it.responseHeaders.add("Location", to)
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        redirect("/live/ad.m3u8", "/applive/mkt.m3u8?app=1")
        redirect("/applive/mkt.m3u8", "/appadhw/cctv.m3u8")
        redirect("/appadhw/cctv.m3u8", "/applive/cctv.m3u8?u=1")
        redirect("/live/ok.m3u8", "/applive/cctv.m3u8?u=1")
        server.createContext("/applive/cctv.m3u8") {
            val body = "#EXTM3U\n".toByteArray()
            it.sendResponseHeaders(200, body.size.toLong())
            it.responseBody.use { out -> out.write(body) }
        }
        server.createContext("/gone") {
            it.sendResponseHeaders(404, -1)
            it.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() = server.stop(0)

    private fun resolve(path: String) = RedirectChain.resolve(base + path, "test", null, 2_000)

    @Test
    fun advertHopInTheMiddleIsCaughtThoughTheEndLooksNormal() {
        assertEquals(RedirectChain.Result.Ad, resolve("/live/ad.m3u8"))
    }

    @Test
    fun normalChainEndsAtTheStream() {
        assertEquals(RedirectChain.Result.Final("$base/applive/cctv.m3u8?u=1"), resolve("/live/ok.m3u8"))
    }

    @Test
    fun directAndBrokenAddresses() {
        assertEquals(RedirectChain.Result.Direct, resolve("/applive/cctv.m3u8"))
        assertEquals(RedirectChain.Result.Failed, resolve("/gone"))
    }
}
