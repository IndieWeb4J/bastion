package dev.jacobandersen.bastion.sigil

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Connection settings for the Sigil IndieAuth service. Bastion validates
 * Micropub bearer tokens by introspecting them against Sigil, presenting the
 * shared [serviceToken] as its own credential.
 */
@ConfigurationProperties(prefix = "bastion.sigil")
data class SigilProperties(
    /** Base URL of the Sigil service, e.g. `https://sigil.example.com`. */
    val baseUrl: String = "",
    /** Shared secret Bastion presents as a bearer credential to Sigil. */
    val serviceToken: String = "",
    /** The identity (profile URL) tokens must have been issued for. */
    val me: String = "",
    /**
     * How long an introspection result is cached. Bounds both the load on Sigil
     * and how long a revoked token may still be accepted (revocation lag).
     */
    val introspectionCacheTtl: Duration = Duration.ofSeconds(30),
    /** Maximum number of cached introspection results before eviction. */
    val introspectionCacheMaxSize: Long = 10_000,
    /** TCP connect timeout for introspection calls. */
    val connectTimeout: Duration = Duration.ofSeconds(3),
    /** Read timeout for introspection calls. */
    val readTimeout: Duration = Duration.ofSeconds(3),
)
