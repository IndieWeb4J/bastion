package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.type.IntrospectionResponse
import dev.jacobandersen.bastion.indieauth.type.Scopes
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Verifies access tokens for resource servers (IndieAuth 6, RFC 7662 plus
 * `me`). Tightly coupled callers such as Micropub keep using the local
 * [AccessTokenService.resolve] lookup; this service shapes the same answer
 * into the interoperable introspection response.
 */
@Service
class IntrospectionService(
    private val accessTokenService: AccessTokenService,
) {
    @Transactional(readOnly = true)
    fun introspect(rawToken: String): IntrospectionResponse {
        val issued = accessTokenService.resolve(rawToken) ?: return IntrospectionResponse(active = false)
        return IntrospectionResponse(
            active = true,
            me = issued.me,
            clientId = issued.clientId,
            scope = Scopes.join(issued.scope).takeIf { it.isNotBlank() },
            exp = issued.expiresAt.epochSecond,
            iat = issued.issuedAt.epochSecond,
        )
    }
}
