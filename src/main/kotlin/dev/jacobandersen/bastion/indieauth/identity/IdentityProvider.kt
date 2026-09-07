package dev.jacobandersen.bastion.indieauth.identity

/**
 * A single identity resolved from an external identity provider after a user
 * authenticates. It carries no local username or password - Bastion has no
 * local accounts - only the provider's stable identifier for the user and an
 * optional display profile.
 */
data class ProviderIdentity(
    /** The provider that issued this identity, e.g. "github". */
    val provider: String,
    /** The provider's stable, unique identifier for the user. */
    val subject: String,
    /** The user's profile URL on the provider, when one exists. */
    val profileUrl: String?,
)

/**
 * Thrown when an [IdentityProvider] cannot resolve an authorization code into
 * an identity (network failure, rejected code, missing response fields, etc.).
 */
class IdentityProviderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Authenticates a browser session against an external identity provider using
 * the OAuth 2.0 authorization code flow and resolves it to a [ProviderIdentity].
 *
 * Adding a new provider is a matter of implementing this interface (plus wiring
 * its configuration); no other code in Bastion needs to know about a provider's
 * specifics. The browser-facing authorization redirect is handled by the UI
 * host (Herald), so providers only expose the code exchange here.
 */
interface IdentityProvider {
    /** The stable name of the provider, used in logs and persistence. */
    val provider: String

    /**
     * Exchanges an authorization code (obtained by the UI host) for the
     * authenticated user's [ProviderIdentity].
     *
     * @throws IdentityProviderException when the code cannot be resolved.
     */
    fun resolveIdentity(code: String): ProviderIdentity
}
