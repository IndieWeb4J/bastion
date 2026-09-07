package dev.jacobandersen.bastion.micropub.data.entity

import dev.jacobandersen.bastion.micropub.data.domain.Token
import dev.jacobandersen.bastion.micropub.security.MicropubToken
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tokens")
class TokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false)
    var token: String,
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    var decoded: MicropubToken,
    @Column(nullable = false)
    var expiresAt: Instant,
) {
    fun toDomain(): Token =
        Token(
            id = requireNotNull(this.id),
            token = this.token,
            decoded = this.decoded,
            expiresAt = expiresAt,
        )
}
