package dev.jacobandersen.bastion.micropub.syndication

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress

class SyndicationHttpClientTest {
    private val mapper: ObjectMapper = JsonMapper.builderWithJackson2Defaults().build()

    private val client = SyndicationHttpClient(mapper)

    private fun target(port: Int): SyndicationConfig.Target =
        SyndicationConfig.Target(
            uid = "target",
            name = "Target",
            endpoint = "http://127.0.0.1:$port/micropub",
        )

    @Test
    fun `sendUpdate posts a JSON action update body`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        var capturedContentType: String? = null
        var capturedBody: String? = null
        server.createContext("/") { exchange ->
            capturedContentType = exchange.requestHeaders.getFirst("Content-Type")
            capturedBody = exchange.requestBody.bufferedReader().use { it.readText() }
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        try {
            val result =
                client.sendUpdate(target(server.address.port), "https://bastion.test/post/1", serializedUpdate())
            assertTrue(result is SyndicationSendResult.Success)
        } finally {
            server.stop(0)
        }

        assertTrue(
            capturedContentType!!.startsWith("application/json"),
            "expected json content type, got $capturedContentType",
        )
        val body = mapper.readTree(capturedBody!!)
        assertEquals("update", body.get("action").asString())
        assertEquals("https://bastion.test/post/1", body.get("url").asString())
        assertEquals(
            "Renamed",
            body
                .get("replace")
                .get("name")
                .get(0)
                .asString(),
        )
        assertEquals(
            "extra",
            body
                .get("add")
                .get("category")
                .get(0)
                .asString(),
        )
        assertEquals("name", body.get("delete").get(0).asString())
    }

    @Test
    fun `sendDelete posts a form-encoded delete action`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        var capturedContentType: String? = null
        var capturedBody: String? = null
        server.createContext("/") { exchange ->
            capturedContentType = exchange.requestHeaders.getFirst("Content-Type")
            capturedBody = exchange.requestBody.bufferedReader().use { it.readText() }
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        try {
            val result = client.sendDelete(target(server.address.port), "https://bastion.test/post/1")
            assertTrue(result is SyndicationSendResult.Success)
        } finally {
            server.stop(0)
        }

        assertTrue(capturedContentType!!.startsWith("application/x-www-form-urlencoded"))
        assertEquals("action=delete&url=https%3A%2F%2Fbastion.test%2Fpost%2F1", capturedBody)
    }

    private fun serializedUpdate(): SyndicationUpdate =
        SyndicationUpdate(
            replace = """{"name":["Renamed"]}""",
            add = """{"category":["extra"]}""",
            delete = """["name"]""",
        )
}
