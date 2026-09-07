package dev.jacobandersen.bastion.indieauth.data.repository

import dev.jacobandersen.bastion.indieauth.data.entity.AccessTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AccessTokenRepository : JpaRepository<AccessTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): AccessTokenEntity?
}
