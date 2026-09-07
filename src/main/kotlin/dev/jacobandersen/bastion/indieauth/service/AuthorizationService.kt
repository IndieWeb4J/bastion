package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.data.entity.AuthRequestEntity
import dev.jacobandersen.bastion.indieauth.data.entity.AuthorizationCodeEntity
import dev.jacobandersen.bastion.indieauth.data.entity.ProviderIdentityEntity
import dev.jacobandersen.bastion.indieauth.data.repository.AuthRequestRepository
import dev.jacobandersen.bastion.indieauth.data.repository.AuthorizationCodeRepository
import dev.jacobandersen.bastion.indieauth.data.repository.ProviderIdentityRepository
import dev.jacobandersen.bastion.indieauth.identity.IdentityProvider
import dev.jacobandersen.bastion.indieauth.identity.IdentityProviderException
import dev.jacobandersen.bastion.indieauth.identity.ProviderIdentity
import dev.jacobandersen.bastion.indieauth.security.Pkce
import dev.jacobandersen.bastion.indieauth.security.Tokens
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import dev.jacobandersen.bastion.indieauth.type.Scopes
import dev.jacobandersen.bastion.indieauth.util.Redirects
import dev.jacobandersen.bastion.indieauth.util.Uris
import dev.jacobandersen.bastion.url.UrlNormalizer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant

private val logger = KotlinLogging.logger {}

/** The parameters a client sends to the IndieAuth authorization endpoint. */
data class AuthorizationRequest(
    val me: String?,
    val clientId: String?,
    val redirectUri: String?,
    val state: String?,
    val scope: String?,
    val responseType: String?,
    val codeChallenge: String?,
    val codeChallengeMethod: String?,
)

/** The outcome of completing the callback: either redirect the browser or reject. */
sealed interface CompleteResult {
    data class Redirect(
        val url: String,
    ) : CompleteResult

    /** The state is invalid, already used, or replayed; there is no trusted redirect target. */
    data object Reject : CompleteResult
}

/**
 * Orchestrates the IndieAuth authorization flow. Bastion has no UI of its own:
 * [begin] persists the client's request against a one-time `state` and hands the
 * browser to the Herald service, and [complete] receives the browser back from
 * Herald with the provider's authorization code, exchanges it for the user's
 * identity, and issues Bastion's own authorization code to the client.
 */
@Service
class AuthorizationService(
    private val config: IndieAuthConfig,
    private val identityProvider: IdentityProvider,
    private val authRequestRepository: AuthRequestRepository,
    private val authorizationCodeRepository: AuthorizationCodeRepository,
    private val providerIdentityRepository: ProviderIdentityRepository,
    @Value($$"${bastion.public-url}") private val publicUrl: String,
) {
    /** Starts the flow, returning the Herald redirect location. */
    @Transactional
    fun begin(request: AuthorizationRequest): String {
        validateResponseType(request.responseType)
        validateMe(request.me)
        validateRedirectUri(request.redirectUri)
        validateClientId(request.clientId)
        val scope = validateScope(request.scope)
        validatePkce(request.codeChallenge, request.codeChallengeMethod)

        if (config.herald.baseUrl.isBlank()) {
            throw IndieAuthException(IndieAuthError.Code.SERVER_ERROR, "The authentication UI host is not configured")
        }

        val state = Tokens.random()
        val now = Instant.now()
        authRequestRepository.save(
            AuthRequestEntity(
                stateHash = Tokens.sha256(state),
                clientId = request.clientId!!,
                redirectUri = request.redirectUri!!,
                me = config.me,
                clientState = request.state,
                scope = Scopes.join(scope),
                codeChallenge = request.codeChallenge?.takeIf { it.isNotBlank() },
                codeChallengeMethod = request.codeChallengeMethod?.takeIf { it.isNotBlank() },
                expiresAt = now.plus(config.authRequestTtl),
                createdAt = now,
            ),
        )

        val returnTo = "${publicUrl.trimEnd('/')}${IndieAuthEndpoints.CALLBACK}"
        return heraldUri(state, request.clientId!!, scope, returnTo)
    }

    /** Completes the flow after Herald returns the browser, producing the client redirect. */
    @Transactional
    fun complete(
        state: String?,
        code: String?,
        error: String?,
    ): CompleteResult {
        if (state.isNullOrBlank()) {
            return CompleteResult.Reject
        }

        val stateHash = Tokens.sha256(state)
        val authRequest = authRequestRepository.findByStateHash(stateHash) ?: return CompleteResult.Reject

        if (authRequest.usedAt != null) {
            logger.warn { "Replayed or reused authorization state rejected" }
            return CompleteResult.Reject
        }

        val claimed = authRequestRepository.claim(stateHash, Instant.now())
        if (claimed == 0) {
            logger.warn { "Replayed or reused authorization state rejected" }
            return CompleteResult.Reject
        }

        if (error != null) {
            return CompleteResult.Redirect(errorRedirect(authRequest, error))
        }

        if (authRequest.expiresAt.isBefore(Instant.now())) {
            return CompleteResult.Redirect(
                errorRedirect(authRequest, IndieAuthError.Code.ACCESS_DENIED.value, "The authorization request has expired"),
            )
        }

        val providerCode =
            code?.takeIf { it.isNotBlank() }
                ?: return CompleteResult.Redirect(
                    errorRedirect(authRequest, IndieAuthError.Code.INVALID_REQUEST.value, "The authorization code is missing"),
                )

        val identity =
            try {
                identityProvider.resolveIdentity(providerCode)
            } catch (e: IdentityProviderException) {
                logger.warn(e) { "Identity provider failed to resolve the authorization code" }
                return CompleteResult.Redirect(errorRedirect(authRequest, IndieAuthError.Code.SERVER_ERROR.value))
            }

        rememberIdentity(identity)

        val authorizationCode = Tokens.random()
        val now = Instant.now()
        authorizationCodeRepository.save(
            AuthorizationCodeEntity(
                codeHash = Tokens.sha256(authorizationCode),
                clientId = authRequest.clientId,
                redirectUri = authRequest.redirectUri,
                me = authRequest.me,
                scope = authRequest.scope,
                codeChallenge = authRequest.codeChallenge,
                codeChallengeMethod = authRequest.codeChallengeMethod,
                expiresAt = now.plus(config.codeTtl),
                createdAt = now,
            ),
        )

        logger.info { "Issued authorization code for ${authRequest.me} (client ${authRequest.clientId})" }

        return CompleteResult.Redirect(codeRedirect(authRequest, authorizationCode))
    }

    private fun rememberIdentity(identity: ProviderIdentity) {
        val now = Instant.now()
        val existing = providerIdentityRepository.findByProviderAndSubject(identity.provider, identity.subject)
        if (existing != null) {
            existing.profileUrl = identity.profileUrl
            existing.me = config.me
            existing.lastSeenAt = now
            providerIdentityRepository.save(existing)
        } else {
            providerIdentityRepository.save(
                ProviderIdentityEntity(
                    provider = identity.provider,
                    subject = identity.subject,
                    profileUrl = identity.profileUrl,
                    me = config.me,
                    createdAt = now,
                    lastSeenAt = now,
                ),
            )
        }
    }

    private fun heraldUri(
        state: String,
        clientId: String,
        scope: List<String>,
        returnTo: String,
    ): String {
        val builder =
            UriComponentsBuilder
                .fromUriString(config.herald.baseUrl.trimEnd('/'))
                .path(config.herald.authorizePath)
                .queryParam("state", state)
                .queryParam("me", config.me)
                .queryParam("client_id", clientId)
                .queryParam("return_to", returnTo)

        if (scope.isNotEmpty()) {
            builder.queryParam("scope", Scopes.join(scope))
        }

        return builder.build().encode().toUriString()
    }

    private fun codeRedirect(
        authRequest: AuthRequestEntity,
        code: String,
    ): String = Redirects.code(authRequest.redirectUri, authRequest.clientState, code)

    private fun errorRedirect(
        authRequest: AuthRequestEntity,
        error: String,
        description: String? = null,
    ): String = Redirects.error(authRequest.redirectUri, authRequest.clientState, error, description)

    private fun validateResponseType(responseType: String?) {
        if (responseType != null && responseType != "code") {
            throw IndieAuthException(IndieAuthError.Code.UNSUPPORTED_RESPONSE_TYPE, "Only the 'code' response type is supported")
        }
    }

    private fun validateMe(me: String?) {
        if (me.isNullOrBlank()) {
            return
        }
        val expected = UrlNormalizer.identity(config.me)
        val actual = UrlNormalizer.identity(me)
        if (expected == null || actual == null || expected != actual) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "The 'me' value does not match this server")
        }
    }

    private fun validateRedirectUri(redirectUri: String?) {
        if (redirectUri.isNullOrBlank() || !Uris.isRedirectUri(redirectUri)) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "A valid 'redirect_uri' is required")
        }
    }

    private fun validateClientId(clientId: String?) {
        if (clientId.isNullOrBlank() || !Uris.isRedirectUri(clientId)) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "A valid 'client_id' URL is required")
        }
    }

    private fun validateScope(scope: String?): List<String> {
        val requested = Scopes.parse(scope)
        val invalid = requested.filterNot { it in config.allowedScopes }
        if (invalid.isNotEmpty()) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_SCOPE, "Unsupported scope requested: ${invalid.joinToString(" ")}")
        }
        return requested
    }

    private fun validatePkce(
        codeChallenge: String?,
        codeChallengeMethod: String?,
    ) {
        if (codeChallenge.isNullOrBlank()) {
            if (!codeChallengeMethod.isNullOrBlank()) {
                throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "A 'code_challenge_method' requires a 'code_challenge'")
            }
            return
        }
        if (codeChallengeMethod != null && !codeChallengeMethod.equals(Pkce.METHOD_S256, ignoreCase = true)) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "Only the 'S256' code challenge method is supported")
        }
    }
}
