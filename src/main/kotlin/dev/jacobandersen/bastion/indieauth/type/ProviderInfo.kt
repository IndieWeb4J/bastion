package dev.jacobandersen.bastion.indieauth.type

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * Public OAuth configuration for a single identity provider that Herald can use
 * to render a login button and redirect the browser to the provider.
 *
 * Only public values are exposed - `client_secret`, token and user-info URLs
 * are never included.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ProviderInfo(
    /** Stable provider identifier, e.g. "github". */
    val provider: String,
    /** OAuth application client id for this provider. */
    val clientId: String,
    /** Provider authorization endpoint the browser should be sent to. */
    val authorizeUrl: String,
    /** Redirect URI Herald should use when initiating the provider flow, if configured. */
    val redirectUri: String? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ProvidersResponse(
    val providers: List<ProviderInfo>,
)
