package dev.jacobandersen.bastion.indieauth.data.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * A pending IndieAuth authorization request, keyed by the one-time `state` hash
 * Bastion generated and handed to the UI host. It binds the client's original
 * request (`clientId`, `redirectUri`, `clientState`, `scope`, PKCE challenge) to
 * that state so the callback can only complete the exact request it started.
 */
@Entity
@Table(name = "indieauth_authorization_requests")
class AuthRequestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false, unique = true)
    var stateHash: String,
    @Column(nullable = false)
    var clientId: String,
    @Column(nullable = false)
    var redirectUri: String,
    @Column(nullable = false)
    var me: String,
    @Column(nullable = true)
    var clientState: String? = null,
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
