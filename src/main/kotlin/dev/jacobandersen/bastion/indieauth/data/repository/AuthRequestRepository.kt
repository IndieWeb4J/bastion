package dev.jacobandersen.bastion.indieauth.data.repository

import dev.jacobandersen.bastion.indieauth.data.entity.AuthRequestEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface AuthRequestRepository : JpaRepository<AuthRequestEntity, UUID> {
    fun findByStateHash(stateHash: String): AuthRequestEntity?

    /**
     * Atomically claims a pending authorization request by stamping [usedAt]
     * only when it is still unused, returning the number of rows affected. This
     * makes the one-time `state` guarantee safe against replay.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update AuthRequestEntity r set r.usedAt = :now where r.stateHash = :stateHash and r.usedAt is null",
    )
    fun claim(
        @Param("stateHash") stateHash: String,
        @Param("now") now: Instant,
    ): Int
}
