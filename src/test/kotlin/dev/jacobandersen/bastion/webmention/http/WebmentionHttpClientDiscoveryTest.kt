package dev.jacobandersen.bastion.webmention.http

import com.sun.net.httpserver.HttpServer
import dev.jacobandersen.bastion.webmention.config.WebmentionConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class WebmentionHttpClientDiscoveryTest {
    private val client = WebmentionHttpClient(WebmentionConfig())

    private fun withServer(
        html: String,
        run: (String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val body = html.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            run("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `skips rel webmention elements without an href and finds the real endpoint`() {
        withServer(
            """<html><head><link rel="webmention"></head><body><a href="/ep" rel="webmention">endpoint</a></body></html>""",
        ) { base ->
            val discovery = client.discoverWebmentionEndpoint("$base/page")
            assertEquals("$base/ep", discovery.endpointUrl)
        }
    }

    @Test
    fun `preserves query string parameters on the discovered endpoint`() {
        withServer(
            """<html><head><link rel="webmention" href="/ep?query=yes"></head></html>""",
        ) { base ->
            val discovery = client.discoverWebmentionEndpoint("$base/page")
            assertEquals("$base/ep?query=yes", discovery.endpointUrl)
        }
    }

    @Test
    fun `returns no endpoint when no rel webmention element has an href`() {
        withServer(
            """<html><head><link rel="webmention"></head><body><a>no rel here</a></body></html>""",
        ) { base ->
            val discovery = client.discoverWebmentionEndpoint("$base/page")
            assertNull(discovery.endpointUrl)
        }
    }
}
