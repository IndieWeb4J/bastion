package dev.jacobandersen.bastion.micropub.syndication

import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.data.service.PostSyndicationService
import dev.jacobandersen.bastion.micropub.type.req.MicropubUpdatePayload
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.util.HttpUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jobrunr.jobs.annotations.Job
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Sends posts to configured downstream micropub syndication targets (such as
 * Bridgy). Syndication is best-effort: work is dispatched asynchronously, every
 * failure is logged rather than retried, and it never affects the outcome of
 * the originating create/update/delete request.
 */
@Service
class SyndicationService(
    private val jobScheduler: JobScheduler,
    private val config: SyndicationConfig,
    private val httpClient: SyndicationHttpClient,
    private val postSyndicationService: PostSyndicationService,
    private val postService: PostService,
    private val urlService: UrlService,
) {
    fun syndicateCreated(
        post: Post,
        requestedUids: Collection<String>,
    ) {
        val targets = config.targetsSupporting(requestedUids, SyndicationAction.CREATE)
        targets.forEach { target ->
            postSyndicationService.record(post.id, target.uid)
            jobScheduler.enqueue<SyndicationService> { it.runCreateJob(post.id, target.uid) }
        }
    }

    fun syndicateDeleted(post: Post) {
        postSyndicationService
            .findByPostId(post.id)
            .mapNotNull { record -> config.targetByUid(record.targetUid)?.takeIf { it.supports(SyndicationAction.DELETE) } }
            .forEach { target ->
                jobScheduler.enqueue<SyndicationService> { it.runDeleteJob(post.id, target.uid) }
            }
    }

    fun syndicateUpdated(
        post: Post,
        update: MicropubUpdatePayload,
    ) {
        val targets =
            postSyndicationService
                .findByPostId(post.id)
                .mapNotNull { record -> config.targetByUid(record.targetUid)?.takeIf { it.supports(SyndicationAction.UPDATE) } }
        if (targets.isEmpty()) return

        val serialized = httpClient.serializeUpdate(update)
        targets.forEach { target ->
            jobScheduler.enqueue<SyndicationService> { it.runUpdateJob(post.id, target.uid, serialized) }
        }
    }

    @Job(retries = 0)
    fun runCreateJob(
        postId: UUID,
        targetUid: String,
    ) {
        try {
            val post = postService.findById(postId)
            if (post == null) {
                logger.warn { "Skipping syndication create: post $postId no longer exists" }
                return
            }
            val target = config.targetByUid(targetUid)
            if (target == null) {
                logger.warn { "Skipping syndication create: unknown target \"$targetUid\"" }
                return
            }
            if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
                logger.warn { "Skipping syndication create for post $postId to blocked target \"$targetUid\"" }
                return
            }

            when (val result = httpClient.sendCreate(target, post.post)) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Syndicated post $postId to target \"$targetUid\" (HTTP ${result.statusCode})" }
                    postSyndicationService.recordOutcome(postId, targetUid, result.location ?: urlService.generatePostUrl(post))
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn { "Syndication create to target \"$targetUid\" for post $postId failed: ${result.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Syndication create to target \"$targetUid\" for post $postId failed unexpectedly" }
        }
    }

    @Job(retries = 0)
    fun runDeleteJob(
        postId: UUID,
        targetUid: String,
    ) {
        try {
            val post = postService.findById(postId)
            if (post == null) {
                logger.warn { "Skipping syndication delete: post $postId no longer exists" }
                return
            }
            val target = config.targetByUid(targetUid)
            if (target == null) {
                logger.warn { "Skipping syndication delete: unknown target \"$targetUid\"" }
                return
            }
            if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
                logger.warn { "Skipping syndication delete for post $postId to blocked target \"$targetUid\"" }
                return
            }

            when (val result = httpClient.sendDelete(target, urlService.generatePostUrl(post))) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Syndication delete sent for post $postId to target \"$targetUid\" (HTTP ${result.statusCode})" }
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn { "Syndication delete to target \"$targetUid\" for post $postId failed: ${result.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Syndication delete to target \"$targetUid\" for post $postId failed unexpectedly" }
        }
    }

    @Job(retries = 0)
    fun runUpdateJob(
        postId: UUID,
        targetUid: String,
        update: SyndicationUpdate,
    ) {
        try {
            val post = postService.findById(postId)
            if (post == null) {
                logger.warn { "Skipping syndication update: post $postId no longer exists" }
                return
            }
            val target = config.targetByUid(targetUid)
            if (target == null) {
                logger.warn { "Skipping syndication update: unknown target \"$targetUid\"" }
                return
            }
            if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
                logger.warn { "Skipping syndication update for post $postId to blocked target \"$targetUid\"" }
                return
            }

            when (val result = httpClient.sendUpdate(target, urlService.generatePostUrl(post), update)) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Syndication update sent for post $postId to target \"$targetUid\" (HTTP ${result.statusCode})" }
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn { "Syndication update to target \"$targetUid\" for post $postId failed: ${result.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Syndication update to target \"$targetUid\" for post $postId failed unexpectedly" }
        }
    }
}
