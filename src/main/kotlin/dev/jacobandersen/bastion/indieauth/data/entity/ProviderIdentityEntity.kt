package dev.jacobandersen.bastion.indieauth.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * A durable mapping from an external provider's subject to the identity (`me`)
 * Bastion issues tokens for. Recorded (and refreshed) on every successful
 * authentication so future provider/user shapes can build on it.
 */
@Entity
@Table(
    name = "indieauth_provider_identities",
    uniqueConstraints = [UniqueConstraint(name = "uq_indieauth_provider_identity", columnNames = ["provider", "subject"])],
)
class ProviderIdentityEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false)
    var provider: String,
    @Column(nullable = false)
    var subject: String,
    @Column(nullable = true)
    var profileUrl: String? = null,
    @Column(nullable = false)
    var me: String,
    @Column(nullable = false)
    var createdAt: Instant,
    @Column(nullable = false)
    var lastSeenAt: Instant,
)
