package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.util.IndieAuthUrls
import dev.jacobandersen.bastion.url.UrlNormalizer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.net.InetAddress
import java.net.URI
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Fetches a client's metadata document to learn its published redirect URLs
 * (IndieAuth 4.2). Follows the fail-open policy: any inconclusive fetch
 * (loopback, DNS failure, network error, unparseable document) returns null
 * and the caller allows the request with a warning. Only a successfully
 * fetched allowlist that lacks the requested `redirect_uri` blocks the
 * request, and only when the redirect host differs from the client host.
 */
@Component
class ClientMetadataFetcher(
    private val objectMapper: ObjectMapper,
) {
    private val client: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT)
                    setReadTimeout(READ_TIMEOUT)
                },
            ).build()

    /**
     * Returns the client's published redirect URIs, an empty set when the
     * document was fetched but publishes none, or null when the fetch was
     * inconclusive (must not fetch, network failure, untrusted document).
     */
    fun fetchRedirectUris(clientId: String): Set<String>? {
        val host = runCatching { URI(clientId) }.getOrNull()?.host?.lowercase() ?: return null
        if (IndieAuthUrls.isLoopbackHost(host)) {
            logger.debug { "Not fetching client metadata for loopback client $clientId" }
            return null
        }
        try {
            if (InetAddress.getByName(host).isLoopbackAddress) {
                logger.debug { "Not fetching client metadata: $host resolves to loopback" }
                return null
            }
        } catch (e: Exception) {
            logger.warn { "Could not resolve client host $host, skipping metadata fetch (${e.message})" }
            return null
        }

        val response =
            try {
                client
                    .get()
                    .uri(clientId)
                    .header(HttpHeaders.ACCEPT, "${MediaType.APPLICATION_JSON_VALUE}, text/html")
                    .retrieve()
                    .toEntity(String::class.java)
            } catch (e: RestClientResponseException) {
                logger.warn { "Client metadata fetch failed for $clientId (HTTP ${e.statusCode.value()})" }
                return null
            } catch (e: Exception) {
                logger.warn { "Client metadata fetch failed for $clientId (${e.message})" }
                return null
            }

        val allowed = mutableSetOf<String>()
        linkHeaderRedirectUris(response.headers, clientId).forEach { allowed.add(it) }

        val body = response.body
        if (!body.isNullOrBlank()) {
            if (isJson(response.headers.contentType, body)) {
                val docUris = jsonRedirectUris(body, clientId) ?: return allowed.ifEmpty { null }
                docUris.forEach { allowed.add(it) }
                return allowed
            }
            htmlRedirectUris(body, clientId).forEach { allowed.add(it) }
        }
        return allowed
    }

    private fun isJson(
        contentType: MediaType?,
        body: String,
    ): Boolean {
        if (contentType != null) {
            if (contentType.includes(MediaType.APPLICATION_JSON)) return true
            if (contentType.type == "application" && contentType.subtype.endsWith("+json")) return true
        }
        return body.trimStart().startsWith("{")
    }

    /**
     * Parses a JSON client metadata document. Returns null when the document
     * is not usable (unparseable, not an object, or its `client_id`
     * contradicts the fetched URL); the caller then falls back to
     * link-header values or reports inconclusive.
     */
    private fun jsonRedirectUris(
        body: String,
        clientId: String,
    ): Set<String>? {
        val node =
            try {
                objectMapper.readTree(body)
            } catch (e: Exception) {
                logger.warn { "Client metadata at $clientId is not valid JSON (${e.message})" }
                return null
            }
        if (!node.isObject) return null
        val docClientId = node.get("client_id")?.asText()
        if (!docClientId.isNullOrBlank()) {
            val expected = UrlNormalizer.identity(clientId)
            val actual = UrlNormalizer.identity(docClientId)
            if (expected == null || actual == null || expected != actual) {
                logger.warn { "Client metadata client_id mismatch at $clientId, ignoring document redirect_uris" }
                return null
            }
        }
        val uris = mutableSetOf<String>()
        node.get("redirect_uris")?.takeIf { it.isArray }?.forEach { uris.add(it.asText()) }
        node.get("redirect_uri")?.takeIf { it.isArray }?.forEach { uris.add(it.asText()) }
        return uris.mapNotNullTo(mutableSetOf()) { resolveAgainst(clientId, it) }
    }

    private fun linkHeaderRedirectUris(
        headers: HttpHeaders,
        clientId: String,
    ): Set<String> {
        val found = mutableSetOf<String>()
        for (header in headers.getOrEmpty(HttpHeaders.LINK)) {
            for (candidate in parseLinkHeader(header, "redirect_uri")) {
                resolveAgainst(clientId, candidate)?.let { found.add(it) }
            }
        }
        return found
    }

    private fun htmlRedirectUris(
        body: String,
        clientId: String,
    ): Set<String> =
        try {
            Jsoup
                .parse(body, clientId)
                .select("link[rel]")
                .filter { el -> el.attr("rel").split(Regex("\\s+")).any { it.equals("redirect_uri", ignoreCase = true) } }
                .mapNotNullTo(mutableSetOf()) { el -> resolveAgainst(clientId, el.attr("abs:href").ifBlank { el.attr("href") }) }
        } catch (e: Exception) {
            logger.warn { "Could not parse client HTML for redirect_uri links at $clientId (${e.message})" }
            emptySet()
        }

    private fun resolveAgainst(
        base: String,
        candidate: String,
    ): String? {
        if (candidate.isBlank()) return null
        return try {
            URI(base).resolve(candidate.trim()).toString()
        } catch (e: Exception) {
            logger.warn { "Ignoring unresolvable redirect URI $candidate from $base (${e.message})" }
            null
        }
    }

    private fun parseLinkHeader(
        header: String,
        rel: String,
    ): List<String> {
        val found = mutableListOf<String>()
        var index = 0
        while (index < header.length) {
            val open = header.indexOf('<', index)
            if (open == -1) break
            val close = header.indexOf('>', open)
            if (close == -1) break
            val target = header.substring(open + 1, close)
            val end = header.indexOf(',', close).let { if (it == -1) header.length else it }
            val params = header.substring(close + 1, end)
            if (params.split(';').any { it.trim().startsWith("rel", ignoreCase = true) && it.contains(rel, ignoreCase = true) }) {
                found.add(target)
            }
            index = end + 1
        }
        return found
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
