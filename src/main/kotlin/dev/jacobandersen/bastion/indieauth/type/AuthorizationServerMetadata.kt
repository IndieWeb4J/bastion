package dev.jacobandersen.bastion.indieauth.type

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * OAuth 2.0 / IndieAuth authorization server metadata (RFC 8414), served from
 * `/.well-known/oauth-authorization-server` so clients can discover Bastion as
 * an IndieAuth provider without any hard-coded endpoints.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AuthorizationServerMetadata(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val responseTypesSupported: List<String> = listOf("code"),
    val grantTypesSupported: List<String> = listOf("authorization_code"),
    val codeChallengeMethodsSupported: List<String> = listOf("S256"),
    val scopesSupported: List<String> = emptyList(),
)
