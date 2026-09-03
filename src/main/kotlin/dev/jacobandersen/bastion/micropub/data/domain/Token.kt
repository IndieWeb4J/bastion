package dev.jacobandersen.bastion.micropub.data.domain

import dev.jacobandersen.bastion.micropub.data.entity.TokenEntity
import dev.jacobandersen.bastion.micropub.security.MicropubToken
import java.time.Instant
import java.util.UUID

data class Token(
    val id: UUID,
    val token: String,
    val decoded: MicropubToken,
    val expiresAt: Instant,
) {
    fun toEntity(): TokenEntity {
        return TokenEntity(id, token, decoded, expiresAt)
    }
}