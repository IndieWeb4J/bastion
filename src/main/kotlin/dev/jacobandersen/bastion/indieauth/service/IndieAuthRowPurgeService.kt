package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.data.repository.AccessTokenRepository
import dev.jacobandersen.bastion.indieauth.data.repository.AuthRequestRepository
import dev.jacobandersen.bastion.indieauth.data.repository.AuthorizationCodeRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Removes dead IndieAuth rows so the authorization-request, authorization-code
 * and access-token tables stay bounded. Expired authorization requests and
 * expired access tokens are deleted outright; authorization codes are kept for
 * a short [retention][IndieAuthConfig.codeRetention] window after they are used
 * or expire, then deleted. Invoked by the recurring JobRunr job in
 * [IndieAuthRowPurgeScheduler].
 */
@Service
class IndieAuthRowPurgeService(
    private val config: IndieAuthConfig,
    private val authRequestRepository: AuthRequestRepository,
    private val authorizationCodeRepository: AuthorizationCodeRepository,
    private val accessTokenRepository: AccessTokenRepository,
) {
    /**
     * No-argument entry point for the recurring JobRunr job. JobRunr stores the
     * job lambda's parameters at registration time and replays them on every
     * fire, so [Instant.now] is computed here inside the invoked method rather
     * than being passed through the scheduled lambda, keeping the purge cutoff
     * fresh on every run.
     */
    @Transactional
    fun purge(): Int = purge(Instant.now())

    @Transactional
    fun purge(now: Instant): Int {
        val authRequests = authRequestRepository.deleteExpired(now)
        val codeCutoff = now.minus(config.codeRetention)
        val codes = authorizationCodeRepository.deleteDead(codeCutoff)
        val tokens = accessTokenRepository.deleteExpired(now)

        logger.info {
            "Purged $authRequests expired authorization requests, $codes dead authorization codes and " +
                "$tokens expired access tokens"
        }
        return authRequests + codes + tokens
    }
}
