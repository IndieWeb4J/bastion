package dev.jacobandersen.bastion.indieauth.identity

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

private val logger = KotlinLogging.logger {}

/**
 * Authenticates a user against GitHub via the OAuth 2.0 authorization code
 * flow. [resolveIdentity] exchanges an authorization code (obtained by the UI
 * host, Herald, from GitHub) for a GitHub access token and resolves it to the
 * user's stable numeric identity. The client secret is only ever used here, for
 * the token exchange, and never leaves Bastion.
 */
@Component
class GitHubIdentityProvider(
    config: IndieAuthConfig,
    private val objectMapper: ObjectMapper,
) : IdentityProvider {
    private val github = config.github

    private val client = RestClient.builder().build()

    override val provider: String = PROVIDER

    override fun resolveIdentity(code: String): ProviderIdentity {
        val accessToken = exchangeCode(code)
        val user = fetchUser(accessToken)
        val subject =
            user["id"]?.asLong()?.toString()
                ?: throw IdentityProviderException("GitHub user response is missing the 'id' field")

        logger.info { "Resolved GitHub identity for subject $subject" }

        return ProviderIdentity(
            provider = PROVIDER,
            subject = subject,
            profileUrl = user["html_url"]?.asText(),
        )
    }

    private fun exchangeCode(code: String): String {
        val body = LinkedMultiValueMap<String, String>()
        body.add("client_id", github.clientId)
        body.add("client_secret", github.clientSecret)
        body.add("code", code)

        val response =
            try {
                client
                    .post()
                    .uri(github.tokenUrl)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String::class.java)
            } catch (e: RestClientResponseException) {
                throw IdentityProviderException("GitHub token exchange failed (HTTP ${e.statusCode.value()})", e)
            }

        val node = objectMapper.readTree(response)
        if (node.has("error")) {
            val error = node["error"]?.asText() ?: "unknown"
            val description = node["error_description"]?.asText().orEmpty()
            throw IdentityProviderException(
                "GitHub token exchange failed: $error${if (description.isBlank()) "" else " - $description"}",
            )
        }

        return node["access_token"]?.asText()?.takeIf { it.isNotBlank() }
            ?: throw IdentityProviderException("GitHub token response is missing the 'access_token' field")
    }

    private fun fetchUser(accessToken: String): JsonNode {
        val response =
            try {
                client
                    .get()
                    .uri(github.userInfoUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(String::class.java)
            } catch (e: RestClientResponseException) {
                throw IdentityProviderException("GitHub user lookup failed (HTTP ${e.statusCode.value()})", e)
            }

        return objectMapper.readTree(response)
    }

    companion object {
        const val PROVIDER = "github"
    }
}
