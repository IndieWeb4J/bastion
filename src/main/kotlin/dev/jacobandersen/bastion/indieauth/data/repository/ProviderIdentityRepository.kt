package dev.jacobandersen.bastion.indieauth.data.repository

import dev.jacobandersen.bastion.indieauth.data.entity.ProviderIdentityEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProviderIdentityRepository : JpaRepository<ProviderIdentityEntity, UUID> {
    fun findByProviderAndSubject(
        provider: String,
        subject: String,
    ): ProviderIdentityEntity?
}
