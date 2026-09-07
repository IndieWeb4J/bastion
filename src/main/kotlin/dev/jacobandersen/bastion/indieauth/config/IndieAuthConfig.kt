package dev.jacobandersen.bastion.indieauth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration for Bastion's IndieAuth provider.
 *
 * Bastion is an IndieAuth *provider*: it authenticates a browser session through
 * an [IdentityProvider][dev.jacobandersen.bastion.indieauth.identity.IdentityProvider]
 * (GitHub, for now) and issues Micropub access tokens for exactly one identity -
 * the site owner identified by [me]. There are no local user accounts and no
 * username/password concept anywhere in Bastion.
 *
 * Because Bastion is API-only and hosts no UI, the browser-facing authentication
 * screen is delegated to a separate service ("Herald") through the [herald]
 * contract described in [HeraldConfig].
 */
@ConfigurationProperties(prefix = "bastion.indieauth")
data class IndieAuthConfig(
    /** The canonical IndieAuth profile URL Bastion represents and issues tokens for. */
    val me: String,
    /** How long an issued authorization code stays valid. */
    val codeTtl: Duration = Duration.ofMinutes(10),
    /** How long a pending authorization request (and its one-time state) stays valid. */
    val authRequestTtl: Duration = Duration.ofMinutes(10),
    /** How long an issued access token stays valid. */
    val accessTokenTtl: Duration = Duration.ofDays(30),
    /** How often the recurring dead-row purge job runs. */
    val purgeInterval: Duration = Duration.ofHours(1),
    /** How long a used or expired authorization-code row is retained before the purge job deletes it. */
    val codeRetention: Duration = Duration.ofDays(1),
    /** The set of scopes Bastion is willing to grant; anything else is `invalid_scope`. */
    val allowedScopes: Set<String> = DEFAULT_SCOPES,
    /** The browser UI host Bastion delegates the authentication screen to. */
    val herald: HeraldConfig = HeraldConfig(),
    /** GitHub identity-provider settings, the only provider shipped today. */
    val github: GitHubConfig = GitHubConfig(),
) {
    /**
     * The contract Bastion uses to hand the authentication screen to the Herald
     * service. This is deliberately provider-agnostic: Bastion redirects the
     * browser to `baseUrl + authorizePath` carrying `state`, `me`, `client_id`,
     * `scope` and `return_to` (Bastion's own callback URL), and Herald returns
     * the browser to that callback carrying `state` and either `code` (success)
     * or `error`.
     */
    data class HeraldConfig(
        /** Base URL of the Herald service. */
        val baseUrl: String = "",
        /** Path (relative to [baseUrl]) Bastion redirects the browser to for the auth UI. */
        val authorizePath: String = "/auth",
    )

    /**
     * GitHub OAuth 2.0 client settings. Only the token exchange and user-info
     * lookup happen here; the browser-facing GitHub redirect is handled by the
     * UI host, so the client secret never leaves Bastion.
     */
    data class GitHubConfig(
        /** OAuth app client id. */
        val clientId: String = "",
        /** OAuth app client secret, used to exchange the authorization code. */
        val clientSecret: String = "",
        /** GitHub authorization endpoint. */
        val authorizeUrl: String = "https://github.com/login/oauth/authorize",
        /** GitHub token endpoint. */
        val tokenUrl: String = "https://github.com/login/oauth/access_token",
        /** GitHub user-info endpoint. */
        val userInfoUrl: String = "https://api.github.com/user",
    )

    companion object {
        val DEFAULT_SCOPES = setOf("create", "update", "delete", "undelete", "media")
    }
}
