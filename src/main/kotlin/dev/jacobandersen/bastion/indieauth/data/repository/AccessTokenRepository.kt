package dev.jacobandersen.bastion.indieauth.data.repository

import dev.jacobandersen.bastion.indieauth.data.entity.AccessTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface AccessTokenRepository : JpaRepository<AccessTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): AccessTokenEntity?

    /**
     * Deletes access tokens that have expired, returning the number of rows
     * removed. Used by the recurring row-purge job so `indieauth_access_tokens`
     * stays bounded.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AccessTokenEntity t where t.expiresAt < :now")
    fun deleteExpired(
        @Param("now") now: Instant,
    ): Int
}
