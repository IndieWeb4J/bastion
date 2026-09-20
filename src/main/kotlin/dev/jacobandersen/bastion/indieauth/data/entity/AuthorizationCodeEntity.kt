package dev.jacobandersen.bastion.indieauth.data.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * An authorization code Bastion issued to a client, keyed by the code hash.
 * Codes are short-lived and single-use; [usedAt] is stamped on first exchange,
 * and any reuse after that is rejected.
 */
@Entity
@Table(name = "indieauth_authorization_codes")
class AuthorizationCodeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false, unique = true)
    var codeHash: String,
    @Column(nullable = false)
    var clientId: String,
    @Column(nullable = false)
    var redirectUri: String,
    @Column(nullable = false)
    var me: String,
    @Column(nullable = false)
    var scope: String,
    @Column(nullable = true)
    var codeChallenge: String? = null,
    @Column(nullable = true)
    var codeChallengeMethod: String? = null,
    @Column(nullable = false)
    var expiresAt: Instant,
    @Column(nullable = true)
    var usedAt: Instant? = null,
    @Column(nullable = false)
    var createdAt: Instant,
)
