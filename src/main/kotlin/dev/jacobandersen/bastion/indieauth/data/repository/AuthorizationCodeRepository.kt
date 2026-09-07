package dev.jacobandersen.bastion.indieauth.data.repository

import dev.jacobandersen.bastion.indieauth.data.entity.AuthorizationCodeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface AuthorizationCodeRepository : JpaRepository<AuthorizationCodeEntity, UUID> {
    fun findByCodeHash(codeHash: String): AuthorizationCodeEntity?

    /**
     * Atomically claims a code by stamping [usedAt] only when it is still
     * unused, returning the number of rows affected. This makes the single-use
     * guarantee safe against concurrent exchanges: exactly one caller receives
     * a non-zero result.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update AuthorizationCodeEntity c set c.usedAt = :now where c.codeHash = :codeHash and c.usedAt is null",
    )
    fun claim(
        @Param("codeHash") codeHash: String,
        @Param("now") now: Instant,
    ): Int

    /**
     * Deletes authorization codes that are dead - claimed before [cutoff], or
     * unclaimed but expired before [cutoff] - returning the number of rows
     * removed. The [cutoff] retention keeps recently used codes around for a
     * short window while bounding the table's growth.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "delete from AuthorizationCodeEntity c where " +
            "(c.usedAt is not null and c.usedAt < :cutoff) or (c.usedAt is null and c.expiresAt < :cutoff)",
    )
    fun deleteDead(
        @Param("cutoff") cutoff: Instant,
    ): Int
}
