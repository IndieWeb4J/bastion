package dev.jacobandersen.bastion.webmention.salmention.data.repository

import dev.jacobandersen.bastion.webmention.salmention.data.entity.SalmentionResponseEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SalmentionResponseRepository : JpaRepository<SalmentionResponseEntity, UUID> {
    fun findBySourceUrlAndResponseUrl(
        sourceUrl: String,
        responseUrl: String,
    ): SalmentionResponseEntity?

    fun findBySourceUrl(sourceUrl: String): List<SalmentionResponseEntity>

    fun findByReceivedWebmentionId(receivedWebmentionId: UUID): List<SalmentionResponseEntity>

    fun deleteByReceivedWebmentionId(receivedWebmentionId: UUID): Int
}
