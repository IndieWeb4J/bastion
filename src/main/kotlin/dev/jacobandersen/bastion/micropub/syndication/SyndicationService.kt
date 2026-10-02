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

    /**
     * Remembers the requested syndication targets of a post that is not yet
     * publicly reachable (a draft or private post). No job is dispatched now -
     * dispatch happens later when the post transitions to public via
     * [syndicatePublished].
     */
    fun retainSyndicationTargets(
        post: Post,
        requestedUids: Collection<String>,
    ) {
        val targets = config.targetsSupporting(requestedUids, SyndicationAction.CREATE)
        targets.forEach { target ->
            postSyndicationService.record(post.id, target.uid)
        }
    }

    /**
     * Dispatches create syndication to every recorded target that supports
     * [SyndicationAction.CREATE]. Used when a retained (draft/private) post
     * transitions to public, so the copy is created only once it is reachable.
     */
    fun syndicatePublished(post: Post) {
        enqueueCreateJobs(post)
    }

    /**
     * Re-creates the downstream copy after an undelete for every recorded
     * target that supports [SyndicationAction.CREATE], symmetric to the delete
     * dispatch that removed it.
     */
    fun syndicateUndeleted(post: Post) {
        enqueueCreateJobs(post)
    }

    /**
     * Dispatches delete syndication for a post whose downstream copy should be
     * retracted (delete, or demotion from public to non-public). Only targets
     * with a recorded syndication outcome (a confirmed copy) and delete support
     * are contacted, so retaining a target for a never-published draft does not
     * produce spurious deletes. [sourceUrl] is the URL the copy was syndicated
     * under (previous URL when the post moved in the same lifecycle step).
     */
    fun syndicateDeleted(
        post: Post,
        sourceUrl: String = urlService.generatePostUrl(post),
    ) {
        postSyndicationService
            .findByPostId(post.id)
            .filter { it.syndicatedUrl != null }
            .mapNotNull { record ->
                config.targetByUid(record.targetUid)?.takeIf { it.supports(SyndicationAction.DELETE) }
            }.forEach { target ->
                jobScheduler.enqueue<SyndicationService> { it.runDeleteJob(post.id, target.uid, sourceUrl) }
            }
    }

    /**
     * Re-bases a syndicated copy onto the post's new URL after a slug rename:
     * the copy registered under [previousUrl] is deleted and re-created at the
     * post's current URL. Only targets that support both [SyndicationAction.DELETE]
     * and [SyndicationAction.CREATE] can re-base, so Bridgy-style targets move
     * their copy instead of silently stranding it at the old URL.
     */
    fun syndicateRebased(
        post: Post,
        previousUrl: String,
    ) {
        recordedTargets(post.id)
            .filter { it.supports(SyndicationAction.DELETE) && it.supports(SyndicationAction.CREATE) }
            .forEach { target ->
                jobScheduler.enqueue<SyndicationService> { it.runRebaseJob(post.id, target.uid, previousUrl) }
            }
    }

    private fun enqueueCreateJobs(post: Post) {
        recordedTargets(post.id)
            .filter { it.supports(SyndicationAction.CREATE) }
            .forEach { target ->
                jobScheduler.enqueue<SyndicationService> { it.runCreateJob(post.id, target.uid) }
            }
    }

    private fun recordedTargets(postId: UUID): List<SyndicationConfig.Target> =
        postSyndicationService
            .findByPostId(postId)
            .mapNotNull { record -> config.targetByUid(record.targetUid) }

    fun syndicateUpdated(
        post: Post,
        update: MicropubUpdatePayload,
    ) {
        val targets = recordedTargets(post.id).filter { it.supports(SyndicationAction.UPDATE) }
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
            if (!post.publiclyReachable) {
                logger.warn { "Skipping syndication create for post $postId: post is no longer publicly reachable" }
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

            val canonicalUrl = urlService.generatePostUrl(post)
            when (val result = httpClient.sendCreate(target, syndicatedPost(post, target, canonicalUrl))) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Syndicated post $postId to target \"$targetUid\" (HTTP ${result.statusCode})" }
                    postSyndicationService.recordOutcome(
                        postId,
                        targetUid,
                        result.location ?: urlService.generatePostUrl(post),
                    )
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
        sourceUrl: String,
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

            when (val result = httpClient.sendDelete(target, sourceUrl)) {
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

    /**
     * Re-bases a syndicated copy after the post moved to a new URL: deletes the
     * copy registered under [previousUrl], then re-creates the post at its
     * current URL in a single job so ordering is deterministic.
     */
    @Job(retries = 0)
    fun runRebaseJob(
        postId: UUID,
        targetUid: String,
        previousUrl: String,
    ) {
        try {
            val post = postService.findById(postId)
            if (post == null) {
                logger.warn { "Skipping syndication rebase: post $postId no longer exists" }
                return
            }
            val target = config.targetByUid(targetUid)
            if (target == null) {
                logger.warn { "Skipping syndication rebase: unknown target \"$targetUid\"" }
                return
            }
            if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
                logger.warn { "Skipping syndication rebase for post $postId to blocked target \"$targetUid\"" }
                return
            }

            when (val deleteResult = httpClient.sendDelete(target, previousUrl)) {
                is SyndicationSendResult.Success -> {
                    logger.info {
                        "Syndication rebase removed old copy for post $postId at target \"$targetUid\" (HTTP ${deleteResult.statusCode})"
                    }
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn {
                        "Syndication rebase could not remove old copy at target \"$targetUid\" for post $postId: ${deleteResult.message}"
                    }
                }
            }

            if (!post.publiclyReachable) {
                logger.warn { "Skipping syndication rebase re-create for post $postId: post is no longer publicly reachable" }
                return
            }

            val canonicalUrl = urlService.generatePostUrl(post)
            when (val createResult = httpClient.sendCreate(target, syndicatedPost(post, target, canonicalUrl))) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Syndication rebase re-created post $postId at target \"$targetUid\" (HTTP ${createResult.statusCode})" }
                    postSyndicationService.recordOutcome(
                        postId,
                        targetUid,
                        createResult.location ?: urlService.generatePostUrl(post),
                    )
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn { "Syndication rebase create to target \"$targetUid\" for post $postId failed: ${createResult.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Syndication rebase to target \"$targetUid\" for post $postId failed unexpectedly" }
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

            val canonicalUrl = urlService.generatePostUrl(post)
            val mappedUpdate = mapUpdateToExcerpt(post, target, canonicalUrl, update)

            when (val result = httpClient.sendUpdate(target, canonicalUrl, mappedUpdate)) {
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

    private fun syndicatedPost(
        post: Post,
        target: SyndicationConfig.Target,
        canonicalUrl: String,
    ) = SyndicationContentMapper.build(post, canonicalUrl, config.effectiveMaxGraphemes(target))

    private fun mapUpdateToExcerpt(
        post: Post,
        target: SyndicationConfig.Target,
        canonicalUrl: String,
        update: SyndicationUpdate,
    ): SyndicationUpdate {
        val excerptText = SyndicationContentMapper.excerptText(post, canonicalUrl, config.effectiveMaxGraphemes(target))
        return httpClient.mapUpdateToExcerpt(update, excerptText, SyndicationContentMapper.includesTitle(post))
    }
}
