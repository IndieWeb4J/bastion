package dev.jacobandersen.bastion.micropub.data.repository

import dev.jacobandersen.bastion.micropub.data.entity.TokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TokenRepository : JpaRepository<TokenEntity, UUID> {
    fun findByToken(token: String): TokenEntity?
}
