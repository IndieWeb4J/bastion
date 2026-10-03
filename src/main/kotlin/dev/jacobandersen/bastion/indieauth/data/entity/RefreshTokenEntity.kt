package dev.jacobandersen.bastion.indieauth.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A refresh token Bastion issued alongside a scoped access token, keyed by
 * the token hash. Only the hash is persisted; the raw token is returned to
 * the client exactly once. Refresh tokens are single-use with rotation: each
 * use claims the old row and issues a replacement.
 */
@Entity
@Table(name = "indieauth_refresh_tokens")
class RefreshTokenEntity(
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
    @Column(nullable = true)
    var usedAt: Instant? = null,
)
