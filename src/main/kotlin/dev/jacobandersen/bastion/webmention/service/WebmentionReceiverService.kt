package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Parser
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.MENTION
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import dev.jacobandersen.bastion.webmention.http.WebmentionSourceFetcher
import dev.jacobandersen.bastion.webmention.salmention.service.SalmentionReceiver
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Asynchronously verifies received webmentions: fetches the source document,
 * confirms it mentions the target (section 3.2.2) and records the outcome
 * (section 3.2.4), extracting interaction type, author and content data when the
 * source is valid.
 */
@Service
class WebmentionReceiverService(
    private val notificationService: ReceivedWebmentionService,
    private val sourceFetcher: WebmentionSourceFetcher,
    private val parser: Mf2Parser,
    private val salmentionReceiver: SalmentionReceiver,
) {
    fun verify(
        sourceUrl: String,
        targetUrl: String,
        postId: UUID,
    ) {
        logger.info { "Verifying received webmention from $sourceUrl for $targetUrl" }
        val received = notificationService.ensurePending(sourceUrl, targetUrl, postId)
        val isReReceipt = received.interaction != null

        val fetch = sourceFetcher.fetch(sourceUrl)
        val verification = WebmentionSourceVerifier.verify(fetch, targetUrl, parser)

        when (verification.verdict) {
            SourceVerdict.GONE -> {
                logger.info { "Source $sourceUrl is gone, marking webmention deleted" }
                notificationService.markDeleted(sourceUrl, postId)
                salmentionReceiver.handleGone(sourceUrl, received.id)
            }

            SourceVerdict.NO_LINK -> {
                logger.warn { "Source $sourceUrl does not link to target $targetUrl" }
                notificationService.markRejected(sourceUrl, postId, "source does not link to the target")
            }

            SourceVerdict.UNREACHABLE -> {
                logger.warn { "Unable to verify source $sourceUrl: ${verification.reason}" }
                notificationService.markError(sourceUrl, postId, verification.reason ?: "unable to fetch source")
            }

            SourceVerdict.VERIFIED -> {
                val analysis =
                    verification.parse?.let(ReceivedWebmentionAnalyzer::analyze)
                        ?: ReceivedWebmentionAnalysis(interaction = MENTION, primary = null)
                logger.info { "Verified webmention from $sourceUrl as ${analysis.interaction}" }
                notificationService.markVerified(sourceUrl, postId, analysis)
                salmentionReceiver.handleVerified(
                    sourceUrl = sourceUrl,
                    receivedWebmentionId = received.id,
                    postId = postId,
                    lastRecheckedAt = received.lastRecheckedAt,
                    parseResult = verification.parse,
                    isReReceipt = isReReceipt,
                )
            }
        }
    }
}
