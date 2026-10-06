package dev.jacobandersen.bastion.sigil

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.security.MessageDigest
import java.util.HexFormat

private val logger = KotlinLogging.logger {}

/**
 * Validates Micropub bearer tokens against the Sigil IndieAuth service using
 * its RFC 7662 introspection endpoint. Bastion holds no token state of its own;
 * Sigil is the source of truth. The service credential configured in
 * [SigilProperties.serviceToken] authenticates Bastion to Sigil.
 *
 * Active results are cached for [SigilProperties.introspectionCacheTtl] so a
 * Micropub request does not incur a network round trip per token. That TTL is
 * therefore also the upper bound on how long a revoked token may still be
 * accepted here. Inactive results are not cached, and any transport failure
 * rejects the token (fail closed).
 */
@Component
class SigilIntrospectionClient(
    private val properties: SigilProperties,
) {
    private val client: RestClient =
        RestClient
            .builder()
            .baseUrl(properties.baseUrl.trimEnd('/'))
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(properties.connectTimeout)
                    setReadTimeout(properties.readTimeout)
                },
            ).build()

    private val cache: Cache<String, SigilIntrospection> =
        Caffeine
            .newBuilder()
            .maximumSize(properties.introspectionCacheMaxSize)
            .expireAfterWrite(properties.introspectionCacheTtl)
            .build()

    /** Introspects [rawToken], returning null when Sigil reports it inactive or unreachable. */
    fun introspect(rawToken: String): SigilIntrospection? {
        val key = cacheKey(rawToken)
        cache.getIfPresent(key)?.let { return it }

        val result =
            try {
                call(rawToken)
            } catch (e: Exception) {
                logger.warn(e) { "Sigil introspection call failed; rejecting token" }
                return null
            } ?: return null

        if (!result.active) {
            return null
        }
        cache.put(key, result)
        return result
    }

    private fun call(rawToken: String): SigilIntrospection? {
        val body = LinkedMultiValueMap<String, String>()
        body.add("token", rawToken)

        return client
            .post()
            .uri("/indieauth/introspect")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.serviceToken}")
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .retrieve()
            .body(SigilIntrospection::class.java)
    }

    private fun cacheKey(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
