package dev.jacobandersen.bastion.webmention.salmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2ParseResult
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.salmention.config.SalmentionConfig
import dev.jacobandersen.bastion.webmention.salmention.data.service.SalmentionResponseService
import dev.jacobandersen.bastion.webmention.service.ReceivedWebmentionAnalyzer
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Orchestrates the Salmention receiving side. On a re-receipt of a previously
 * verified source it re-extracts nested responses and diffs them against what
 * is already stored: new responses are ingested, responses whose snapshot
 * changed on the source are refreshed, and responses that are no longer present
 * are retired. It then triggers the upstream re-send when the diff changed the
 * stored set or the re-verification changed the received response itself; a
 * diff with no change is a no-op. A first-time acceptance only triggers the
 * re-send.
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

        val changed = diffAndIngest(sourceUrl, receivedWebmentionId, parseResult)
        if (changed || receivedResponseUpdated) {
            salmentionSender.resendToActiveTargets(postUrl)
        }
    }

    fun handleGone(
        sourceUrl: String,
        receivedWebmentionId: UUID,
        postId: UUID,
    ) {
        if (!config.enabled) return
        val retired = salmentionResponseService.retireByReceivedWebmention(receivedWebmentionId)
        if (retired > 0) {
            resolvePostUrl(postId)?.let { salmentionSender.resendToActiveTargets(it) }
        }
    }

    /**
     * Reconciles the stored nested responses for [receivedWebmentionId] with
     * what the freshly re-fetched [parseResult] currently displays. Returns
     * whether anything changed (a response was ingested, refreshed or retired);
     * an unchanged diff is a no-op.
     */
    fun diffAndIngest(
        sourceUrl: String,
        receivedWebmentionId: UUID,
        parseResult: Mf2ParseResult?,
    ): Boolean {
        val current =
            parseResult
                ?.let { NestedResponseExtractor.extract(it, sourceUrl) }
                .orEmpty()
                .distinctBy { it.responseUrl }
                .associateBy { it.responseUrl }
        val existing =
            salmentionResponseService
                .byReceivedWebmention(receivedWebmentionId)
                .associateBy { it.responseUrl }
        var changed = false

        current.forEach { (responseUrl, nested) ->
            val analysis = analyze(nested.entry)
            val stored = existing[responseUrl]
            when {
                stored == null -> {
                    salmentionResponseService.ingest(sourceUrl, receivedWebmentionId, responseUrl, analysis)
                    changed = true
                }

                !stored.matchesAnalysis(analysis) -> {
                    salmentionResponseService.refresh(receivedWebmentionId, responseUrl, analysis)
                    changed = true
                }
            }
        }

        val removed = existing.keys - current.keys
        if (removed.isNotEmpty()) {
            salmentionResponseService.retireRemoved(receivedWebmentionId, current.keys)
            changed = true
        }
        return changed
    }

    private fun analyze(entry: Mf2Object): ReceivedWebmentionAnalysis =
        ReceivedWebmentionAnalyzer.analyze(
            Mf2ParseResult(
                items = listOf(entry),
                rels = emptyMap(),
                relUrls = emptyMap(),
            ),
        )

    private fun resolvePostUrl(postId: UUID): String? {
        val post = postService.findById(postId) ?: return null
        return runCatching { urlService.generatePostUrl(post) }.getOrNull()
    }
}
