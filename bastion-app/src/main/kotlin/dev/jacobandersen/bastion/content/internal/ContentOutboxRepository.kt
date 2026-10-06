package dev.jacobandersen.bastion.content.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ContentOutboxRepository : JpaRepository<ContentOutboxEntity, UUID> {
    fun findTop100ByPublishedAtUtcIsNullOrderByCreatedAtUtcAsc(): List<ContentOutboxEntity>
}
