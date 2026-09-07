package dev.jacobandersen.bastion.webmention.salmention.service

import dev.jacobandersen.bastion.microformats2.Mf2ParseResult
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.salmention.config.SalmentionConfig
import dev.jacobandersen.bastion.webmention.salmention.data.service.SalmentionResponseService
import dev.jacobandersen.bastion.webmention.service.ReceivedWebmentionAnalyzer
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Orchestrates the Salmention receiving side. On a re-receipt of a previously
 * verified source it re-extracts nested responses, diffs them by `u-url`
 * against what was already stored and ingests only the new ones; it then
 * triggers the upstream re-send when a new nested response was ingested or the
 * re-verification changed the received response itself. A first-time
 * acceptance only triggers the re-send.
 */
@Service
class SalmentionReceiver(
    private val salmentionResponseService: SalmentionResponseService,
    private val salmentionSender: SalmentionSender,
    private val postService: PostService,
    private val urlService: UrlService,
    private val config: SalmentionConfig,
) {
    fun handleVerified(
        sourceUrl: String,
        receivedWebmentionId: UUID,
        postId: UUID,
        parseResult: Mf2ParseResult?,
        isReReceipt: Boolean,
        receivedResponseUpdated: Boolean,
    ) {
        if (!config.enabled) return

        val postUrl = resolvePostUrl(postId) ?: return

        if (!isReReceipt) {
            salmentionSender.resendToActiveTargets(postUrl)
            return
        }

        val ingested = diffAndIngest(sourceUrl, receivedWebmentionId, parseResult)
        if (ingested || receivedResponseUpdated) {
            salmentionSender.resendToActiveTargets(postUrl)
        }
    }

    fun handleGone(
        sourceUrl: String,
        receivedWebmentionId: UUID,
    ) {
        if (!config.enabled) return
        salmentionResponseService.retireByReceivedWebmention(receivedWebmentionId)
    }

    fun diffAndIngest(
        sourceUrl: String,
        receivedWebmentionId: UUID,
        parseResult: Mf2ParseResult?,
    ): Boolean {
        val nested =
            parseResult
                ?.let(NestedResponseExtractor::extract)
                .orEmpty()
                .distinctBy { it.responseUrl }
        if (nested.isEmpty()) return false

        val existing = salmentionResponseService.responseUrlsByReceivedWebmention(receivedWebmentionId)
        val capacity = (config.maxNestedResponsesPerSource - existing.size).coerceAtLeast(0)
        val new = nested.filter { it.responseUrl !in existing }.take(capacity)
        if (new.isEmpty()) return false

        new.forEach { response ->
            val analysis =
                ReceivedWebmentionAnalyzer.analyze(
                    Mf2ParseResult(items = listOf(response.entry), rels = emptyMap(), relUrls = emptyMap()),
                )
            salmentionResponseService.ingest(sourceUrl, receivedWebmentionId, response.responseUrl, analysis)
        }
        return true
    }

    private fun resolvePostUrl(postId: UUID): String? {
        val post = postService.findById(postId) ?: return null
        return runCatching { urlService.generatePostUrl(post) }.getOrNull()
    }
}
