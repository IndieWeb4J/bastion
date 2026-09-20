package dev.jacobandersen.bastion.indieauth.data.entity

import dev.jacobandersen.bastion.indieauth.data.domain.IssuedAccessToken
import dev.jacobandersen.bastion.indieauth.type.Scopes
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * An access token Bastion issued, keyed by the token hash. Only the hash is
 * persisted; the raw token is returned to the client exactly once and cannot be
 * recovered from the database.
 */
@Entity
@Table(name = "indieauth_access_tokens")
class AccessTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false, unique = true)
    var tokenHash: String,
    @Column(nullable = false)
    var me: String,
    @Column(nullable = false)
    var clientId: String,
    @Column(nullable = false)
    var scope: String,
    @Column(nullable = false)
    var issuedAt: Instant,
    @Column(nullable = false)
    var expiresAt: Instant,
) {
    fun toDomain(): IssuedAccessToken =
        IssuedAccessToken(
            me = me,
            clientId = clientId,
            scope = Scopes.parse(scope),
            expiresAt = expiresAt,
        )
}
