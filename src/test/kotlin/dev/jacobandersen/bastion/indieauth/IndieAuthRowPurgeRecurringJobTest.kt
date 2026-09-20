package dev.jacobandersen.bastion.indieauth

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.indieauth.data.entity.AuthRequestEntity
import dev.jacobandersen.bastion.indieauth.data.repository.AuthRequestRepository
import dev.jacobandersen.bastion.indieauth.security.Pkce
import dev.jacobandersen.bastion.indieauth.security.Tokens
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Duration
import java.time.Instant

/**
 * Exercises the *recurring* purge job end to end against a live JobRunr
 * background server, not just [IndieAuthRowPurgeService.purge] called directly.
 *
 * JobRunr serializes the recurring job's job details at registration time
 * (context startup) and replays them on every fire. A scheduler that passes a
 * captured `Instant.now()` through the scheduled lambda therefore freezes the
 * purge cutoff at process start, and rows that expire after startup accumulate
 * forever. The scheduler instead registers the no-argument [IndieAuthRowPurgeService.purge]
 * entry point, which computes the cutoff inside the invoked method on every
 * fire. This test inserts an authorization-request row that expires *after*
 * registration, and requires the recurring job to delete it - which only
 * happens when a fire computes a fresh cutoff.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=true",
        "jobrunr.background-job-server.poll-interval-in-seconds=5",
        "jobrunr.miscellaneous.allow-anonymous-data-usage=false",
        "bastion.indieauth.purge-interval=5s",
        "bastion.indieauth.code-retention=1d",
    ],
)
class IndieAuthRowPurgeRecurringJobTest {
    @Autowired
    lateinit var authRequestRepository: AuthRequestRepository

    private val clientId = "https://client.example"
    private val redirectUri = "https://client.example/callback"
    private val challenge = Pkce.s256("purge-probe-verifier")

    @Test
    fun `recurring purge job removes a row that expires after job registration`() {
        val now = Instant.now()

        // Positive control: already expired at insert time, removed by the next fire.
        val controlHash = Tokens.sha256("control-expired-before-insert")
        authRequestRepository.save(row(controlHash, now.minusSeconds(60)))
        // Discriminator: expires AFTER the recurring job was registered, so only a
        // fresh per-fire cutoff can ever delete it.
        val midRunHash = Tokens.sha256("expires-mid-run")
        authRequestRepository.save(row(midRunHash, now.plusSeconds(5)))

        // Fires are happening at all.
        assertPurgedWithin(
            controlHash,
            Duration.ofSeconds(20),
            "control row (expired at insert) was not purged by a recurring fire",
        )

        // The row that expired after registration is gone too - the cutoff is fresh per fire.
        assertPurgedWithin(
            midRunHash,
            Duration.ofSeconds(45),
            "row expiring after job registration was never purged - the cutoff may be frozen at registration time",
        )
    }

    private fun row(
        stateHash: String,
        expiresAt: Instant,
    ) = AuthRequestEntity(
        stateHash = stateHash,
        clientId = clientId,
        redirectUri = redirectUri,
        me = "https://bastion.test",
        scope = "create",
        codeChallenge = challenge,
        codeChallengeMethod = "S256",
        expiresAt = expiresAt,
        createdAt = Instant.now(),
    )

    private fun assertPurgedWithin(
        stateHash: String,
        timeout: Duration,
        message: String,
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (authRequestRepository.findByStateHash(stateHash) == null) {
                return
            }
            Thread.sleep(250)
        }
        org.junit.jupiter.api.Assertions
            .fail<Unit>(message)
    }
}
