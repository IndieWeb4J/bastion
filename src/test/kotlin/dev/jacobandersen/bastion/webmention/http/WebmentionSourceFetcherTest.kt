package dev.jacobandersen.bastion.webmention.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.jacobandersen.bastion.webmention.config.WebmentionConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class WebmentionSourceFetcherTest {
    private fun fetcher(blockedHosts: Set<String> = emptySet()) =
        WebmentionSourceFetcher(
            WebmentionConfig(),
            SourceHostValidator { url -> blockedHosts.any { url.contains(it) } },
        )

    private fun withServer(
        handler: (HttpExchange) -> Unit,
        run: (String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            handler(exchange)
            exchange.close()
        }
        server.start()
        try {
            run("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun defaultValidatorBlocksLoopbackSources() {
        val blocked = WebmentionSourceFetcher(WebmentionConfig(), DefaultSourceHostValidator())
        val fetch = blocked.fetch("http://127.0.0.1/secret")
        assertEquals(0, fetch.statusCode)
        assertTrue(fetch.error!!.contains("blocked address"), "unexpected error: ${fetch.error}")
    }

    @Test
    fun fetchesAValidSourceWithStatusAndBody() {
        withServer({
            respond(
                it,
                200,
                "<div class=\"h-entry\">hi</div>",
                mapOf("Content-Type" to "text/html")
            )
        }) { base ->
            val fetch = fetcher().fetch("$base/post")
            assertEquals(200, fetch.statusCode)
            assertEquals("$base/post", fetch.finalUrl)
            assertTrue(fetch.body.contains("h-entry"))
        }
    }

    @Test
    fun followsRedirectsToAnAllowedTarget() {
        withServer({ exchange ->
            when (exchange.requestURI.path) {
                "/old" -> respond(exchange, 301, "", mapOf("Location" to "/new"))
                "/new" -> respond(exchange, 200, "<html>ok</html>", mapOf("Content-Type" to "text/html"))
                else -> respond(exchange, 404, "")
            }
        }) { base ->
            val fetch = fetcher().fetch("$base/old")
            assertEquals(200, fetch.statusCode)
            assertEquals("$base/new", fetch.finalUrl)
        }
    }

    @Test
    fun rejectsRedirectsToABlockedHost() {
        withServer({ respond(it, 302, "", mapOf("Location" to "http://blocked.example/secret")) }) { base ->
            val fetch = fetcher(blockedHosts = setOf("blocked.example")).fetch("$base/start")
            assertEquals(0, fetch.statusCode)
            assertTrue(fetch.error!!.contains("blocked address"), "unexpected error: ${fetch.error}")
        }
    }

    @Test
    fun rejectsRedirectsToNonHttpSchemes() {
        withServer({ respond(it, 302, "", mapOf("Location" to "file:///etc/passwd")) }) { base ->
            val fetch = fetcher().fetch("$base/start")
            assertEquals(0, fetch.statusCode)
        }
    }

    @Test
    fun surfacesA404AsGoneStatusInsteadOfNetworkError() {
        withServer({ respond(it, 404, "gone") }) { base ->
            val fetch = fetcher().fetch("$base/old-post")
            assertEquals(404, fetch.statusCode)
        }
    }

    @Test
    fun surfacesA410AsGoneStatus() {
        withServer({ respond(it, 410, "gone") }) { base ->
            val fetch = fetcher().fetch("$base/old-post")
            assertEquals(410, fetch.statusCode)
        }
    }

    @Test
    fun givesUpAfterTooManyRedirects() {
        withServer({ respond(it, 302, "", mapOf("Location" to "/loop")) }) { base ->
            val fetch = fetcher().fetch("$base/loop")
            assertEquals(0, fetch.statusCode)
            assertEquals("too many redirects", fetch.error)
        }
    }
}
