package dev.jacobandersen.bastion.indieauth.data.repository

import dev.jacobandersen.bastion.indieauth.data.entity.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    fun deleteByTokenHash(tokenHash: String): Int

    /**
     * Atomically claims a refresh token by stamping [usedAt] only when it is
     * still unused, returning the number of rows affected. Rotation is safe
     * against concurrent use: exactly one caller receives a non-zero result.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update RefreshTokenEntity t set t.usedAt = :now where t.tokenHash = :tokenHash and t.usedAt is null",
    )
    fun claim(
        @Param("tokenHash") tokenHash: String,
        @Param("now") now: Instant,
    ): Int

    /**
     * Deletes refresh tokens that are dead - used before [cutoff], or unused
     * but expired - returning the number of rows removed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "delete from RefreshTokenEntity t where " +
            "(t.usedAt is not null and t.usedAt < :cutoff) or (t.usedAt is null and t.expiresAt < :cutoff)",
    )
    fun deleteDead(
        @Param("cutoff") cutoff: Instant,
    ): Int
}
