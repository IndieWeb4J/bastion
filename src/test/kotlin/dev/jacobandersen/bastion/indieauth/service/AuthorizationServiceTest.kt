package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.data.entity.AuthRequestEntity
import dev.jacobandersen.bastion.indieauth.data.entity.AuthorizationCodeEntity
import dev.jacobandersen.bastion.indieauth.data.repository.AuthRequestRepository
import dev.jacobandersen.bastion.indieauth.data.repository.AuthorizationCodeRepository
import dev.jacobandersen.bastion.indieauth.identity.IdentityProvider
import dev.jacobandersen.bastion.indieauth.identity.IdentityProviderException
import dev.jacobandersen.bastion.indieauth.identity.ProviderIdentity
import dev.jacobandersen.bastion.indieauth.security.Pkce
import dev.jacobandersen.bastion.indieauth.security.Tokens
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant

class AuthorizationServiceTest {
    private val config =
        IndieAuthConfig(
            me = "https://bastion.test",
            herald = IndieAuthConfig.HeraldConfig(baseUrl = "https://herald.test", authorizePath = "/auth"),
        )
    private val identityProvider = mock(IdentityProvider::class.java)
    private val authRequestRepository = mock(AuthRequestRepository::class.java)
    private val authorizationCodeRepository = mock(AuthorizationCodeRepository::class.java)
    private val ownerVerifier = mock(OwnerVerifier::class.java)
    private val service =
        AuthorizationService(
            config,
            identityProvider,
            authRequestRepository,
            authorizationCodeRepository,
            ownerVerifier,
            "https://bastion.test",
        )

    private val clientId = "https://client.example"
    private val redirectUri = "https://client.example/callback"
    private val verifier = "client-verifier"
    private val challenge = Pkce.s256(verifier)

    private fun request(
        me: String? = null,
        clientId: String? = this.clientId,
        redirectUri: String? = this.redirectUri,
        state: String? = "client-state",
        scope: String? = "create",
        responseType: String? = "code",
        codeChallenge: String? = challenge,
        codeChallengeMethod: String? = Pkce.METHOD_S256,
    ) = AuthorizationRequest(
        me = me,
        clientId = clientId,
        redirectUri = redirectUri,
        state = state,
        scope = scope,
        responseType = responseType,
        codeChallenge = codeChallenge,
        codeChallengeMethod = codeChallengeMethod,
    )

    private fun authRequest(
        stateHash: String,
        clientState: String? = "client-state",
        redirectUri: String = this.redirectUri,
        codeChallenge: String? = null,
        expiresAt: Instant = Instant.now().plusSeconds(60),
    ) = AuthRequestEntity(
        stateHash = stateHash,
        clientId = clientId,
        redirectUri = redirectUri,
        me = "https://bastion.test",
        clientState = clientState,
        scope = "create",
        codeChallenge = codeChallenge,
        expiresAt = expiresAt,
        createdAt = Instant.now(),
    )

    // ------------------------------------------------------------------ begin

    @Test
    fun `begin redirects to herald and persists a one-time state`() {
        val location = service.begin(request())

        assertTrue(location.startsWith("https://herald.test/auth?"))
        val state = queryParam(location, "state")
        assertTrue(!state.isNullOrBlank())
        assertEquals("https://bastion.test", queryParam(location, "me"))
        assertEquals(clientId, queryParam(location, "client_id"))
        assertEquals("https://bastion.test/indieauth/auth/callback", queryParam(location, "return_to"))

        val captor = ArgumentCaptor.forClass(AuthRequestEntity::class.java)
        verify(authRequestRepository).save(captor.capture())
        assertEquals(Tokens.sha256(state!!), captor.value.stateHash)
        assertEquals(redirectUri, captor.value.redirectUri)
        assertEquals(challenge, captor.value.codeChallenge)
        assertEquals(Pkce.METHOD_S256, captor.value.codeChallengeMethod)
    }

    @Test
    fun `begin requires a code challenge`() {
        assertCode(IndieAuthError.Code.INVALID_REQUEST) {
            service.begin(
                request(
                    codeChallenge = null,
                    codeChallengeMethod = null,
                ),
            )
        }
    }

    @Test
    fun `begin requires a code challenge even when the method is present`() {
        assertCode(IndieAuthError.Code.INVALID_REQUEST) {
            service.begin(request(codeChallenge = null, codeChallengeMethod = Pkce.METHOD_S256))
        }
    }

    @Test
    fun `begin accepts an omitted code challenge method as s256`() {
        val location = service.begin(request(codeChallengeMethod = null))

        assertTrue(location.startsWith("https://herald.test/auth?"))
        val captor = ArgumentCaptor.forClass(AuthRequestEntity::class.java)
        verify(authRequestRepository).save(captor.capture())
        assertEquals(challenge, captor.value.codeChallenge)
        assertTrue(captor.value.codeChallengeMethod.isNullOrBlank())
    }

    @Test
    fun `begin rejects an invalid redirect uri`() {
        assertCode(IndieAuthError.Code.INVALID_REQUEST) { service.begin(request(redirectUri = "not-a-url")) }
    }

    @Test
    fun `begin rejects a redirect uri with a fragment`() {
        assertCode(IndieAuthError.Code.INVALID_REQUEST) { service.begin(request(redirectUri = "$redirectUri#fragment")) }
    }

    @Test
    fun `begin rejects a disallowed scope`() {
        assertCode(IndieAuthError.Code.INVALID_SCOPE) { service.begin(request(scope = "create admin")) }
    }

    @Test
    fun `begin rejects an unsupported code challenge method`() {
        assertCode(IndieAuthError.Code.INVALID_REQUEST) {
            service.begin(request(codeChallenge = "challenge", codeChallengeMethod = "plain"))
        }
    }

    @Test
    fun `begin rejects a mismatched me`() {
        assertCode(IndieAuthError.Code.INVALID_REQUEST) { service.begin(request(me = "https://someone.else")) }
    }

    @Test
    fun `begin rejects an unsupported response type`() {
        assertCode(IndieAuthError.Code.UNSUPPORTED_RESPONSE_TYPE) { service.begin(request(responseType = "token")) }
    }

    // --------------------------------------------------------------- complete

    @Test
    fun `complete issues a code for a valid state`() {
        val state = "one-time-state"
        val stateHash = Tokens.sha256(state)
        `when`(authRequestRepository.findByStateHash(stateHash)).thenReturn(authRequest(stateHash))
        `when`(authRequestRepository.claim(eq(stateHash), any())).thenReturn(1)
        `when`(identityProvider.resolveIdentity("github-code"))
            .thenReturn(ProviderIdentity("github", "12345", "https://github.com/someone"))
        `when`(ownerVerifier.verify("https://github.com/someone")).thenReturn(OwnerVerification.Verified)

        val result = service.complete(state, "github-code", null)

        assertTrue(result is CompleteResult.Redirect)
        val location = (result as CompleteResult.Redirect).url
        assertTrue(location.startsWith("$redirectUri?"))
        assertEquals("client-state", queryParam(location, "state"))
        val code = queryParam(location, "code")
        assertTrue(!code.isNullOrBlank())

        val codeCaptor = ArgumentCaptor.forClass(AuthorizationCodeEntity::class.java)
        verify(authorizationCodeRepository).save(codeCaptor.capture())
        assertEquals(Tokens.sha256(code!!), codeCaptor.value.codeHash)
    }

    @Test
    fun `complete redirects an access denied error when the identity is not the owner`() {
        val stateHash = Tokens.sha256("one-time-state")
        `when`(authRequestRepository.findByStateHash(stateHash)).thenReturn(authRequest(stateHash))
        `when`(authRequestRepository.claim(eq(stateHash), any())).thenReturn(1)
        `when`(identityProvider.resolveIdentity("github-code"))
            .thenReturn(ProviderIdentity("github", "12345", "https://github.com/attacker"))
        `when`(ownerVerifier.verify("https://github.com/attacker")).thenReturn(OwnerVerification.NotLinked)

        val result = service.complete("one-time-state", "github-code", null)

        assertTrue(result is CompleteResult.Redirect)
        assertEquals("access_denied", queryParam((result as CompleteResult.Redirect).url, "error"))
        verify(authorizationCodeRepository, never()).save(any())
    }

    @Test
    fun `complete rejects an unknown state`() {
        `when`(authRequestRepository.findByStateHash(anyString())).thenReturn(null)

        assertEquals(CompleteResult.Reject, service.complete("unknown-state", "github-code", null))
        verify(authorizationCodeRepository, never()).save(any())
    }

    @Test
    fun `complete rejects a replayed state`() {
        val stateHash = Tokens.sha256("one-time-state")
        `when`(authRequestRepository.findByStateHash(stateHash)).thenReturn(authRequest(stateHash))
        `when`(authRequestRepository.claim(eq(stateHash), any())).thenReturn(0)

        assertEquals(CompleteResult.Reject, service.complete("one-time-state", "github-code", null))
    }

    @Test
    fun `complete redirects an error from the ui host`() {
        val stateHash = Tokens.sha256("one-time-state")
        `when`(authRequestRepository.findByStateHash(stateHash)).thenReturn(authRequest(stateHash))
        `when`(authRequestRepository.claim(eq(stateHash), any())).thenReturn(1)

        val result = service.complete("one-time-state", null, "access_denied")

        assertTrue(result is CompleteResult.Redirect)
        assertEquals("access_denied", queryParam((result as CompleteResult.Redirect).url, "error"))
        verify(identityProvider, never()).resolveIdentity(anyString())
    }

    @Test
    fun `complete redirects a server error when the provider fails`() {
        val stateHash = Tokens.sha256("one-time-state")
        `when`(authRequestRepository.findByStateHash(stateHash)).thenReturn(authRequest(stateHash))
        `when`(authRequestRepository.claim(eq(stateHash), any())).thenReturn(1)
        `when`(identityProvider.resolveIdentity(anyString())).thenThrow(IdentityProviderException("boom"))

        val result = service.complete("one-time-state", "github-code", null)

        assertTrue(result is CompleteResult.Redirect)
        assertEquals("server_error", queryParam((result as CompleteResult.Redirect).url, "error"))
    }

    @Test
    fun `complete redirects a server error when the provider fails unexpectedly`() {
        val stateHash = Tokens.sha256("one-time-state")
        `when`(authRequestRepository.findByStateHash(stateHash)).thenReturn(authRequest(stateHash))
        `when`(authRequestRepository.claim(eq(stateHash), any())).thenReturn(1)
        `when`(identityProvider.resolveIdentity(anyString())).thenThrow(RuntimeException("boom"))

        val result = service.complete("one-time-state", "github-code", null)

        assertTrue(result is CompleteResult.Redirect)
        assertEquals("server_error", queryParam((result as CompleteResult.Redirect).url, "error"))
        verify(authorizationCodeRepository, never()).save(any())
    }

    @Test
    fun `complete redirects a server error when owner verification fails unexpectedly`() {
        val stateHash = Tokens.sha256("one-time-state")
        `when`(authRequestRepository.findByStateHash(stateHash)).thenReturn(authRequest(stateHash))
        `when`(authRequestRepository.claim(eq(stateHash), any())).thenReturn(1)
        `when`(identityProvider.resolveIdentity("github-code"))
            .thenReturn(ProviderIdentity("github", "12345", "https://github.com/someone"))
        `when`(ownerVerifier.verify(anyString())).thenThrow(RuntimeException("boom"))

        val result = service.complete("one-time-state", "github-code", null)

        assertTrue(result is CompleteResult.Redirect)
        assertEquals("server_error", queryParam((result as CompleteResult.Redirect).url, "error"))
        verify(authorizationCodeRepository, never()).save(any())
    }

    // --------------------------------------------------------------- helpers

    private fun queryParam(
        url: String,
        name: String,
    ): String? =
        UriComponentsBuilder
            .fromUriString(url)
            .build()
            .queryParams
            .getFirst(name)

    private fun assertCode(
        code: IndieAuthError.Code,
        block: () -> Unit,
    ) {
        val e = assertThrows(IndieAuthException::class.java) { block() }
        assertEquals(code, e.code)
    }
}
