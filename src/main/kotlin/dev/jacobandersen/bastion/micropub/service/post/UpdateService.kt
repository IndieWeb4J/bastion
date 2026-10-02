package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.data.service.PostSyndicationService
import dev.jacobandersen.bastion.micropub.service.MicropubCommandResolver
import dev.jacobandersen.bastion.micropub.syndication.SyndicationService
import dev.jacobandersen.bastion.micropub.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.req.MicropubUpdatePayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.service.WebmentionService
import dev.jacobandersen.bastion.websub.service.WebsubPublisher
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class UpdateService(
    private val urlService: UrlService,
    private val postService: PostService,
    private val commandResolver: MicropubCommandResolver,
    private val webmentionService: WebmentionService,
    private val websubPublisher: WebsubPublisher,
    private val syndicationService: SyndicationService,
    private val postSyndicationService: PostSyndicationService,
) {
    @PreAuthorize("hasAuthority('UPDATE')")
    fun update(payload: MicropubPayload): ApiResponse<*> {
        val update =
            try {
                payload.asUpdatePayload().withoutUntaggedCategories()
            } catch (e: Exception) {
                return ApiResponse.Error.InvalidRequest(errorDescription = "Failed to parse payload as update: ${e.message}")
            }

        if (update.replacements == null && update.additions == null && update.removals == null) {
            return ApiResponse.Error.InvalidRequest(
                errorDescription = "Update payload must include at least one of replace, add, or delete",
            )
        }

        val slug =
            urlService.extractPostSlug(update.url)
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid URL for this Bastion instance: ${update.url}")

        val post =
            postService.findBySlug(slug)
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Post not found for URL: ${update.url}")

        val commands =
            commandResolver.resolve { key ->
                update.replacements?.get(key) ?: update.additions?.get(key)
            } ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid command parameters in update")

        val previousUrl = urlService.generatePostUrl(post)
        val wasPublic = post.publiclyReachable
        val previousTargetUrls = webmentionService.targetUrlsOf(post.post)

        var postObj = post.post

        update.replacements?.forEach { (key, values) ->
            if (!MicropubCommand.isCommandProperty(key)) {
                postObj = if (values.isEmpty()) postObj.deleteProperty(key) else postObj.setProperty(key, values)
            }
        }

        update.additions?.forEach { (key, values) ->
            if (!MicropubCommand.isCommandProperty(key)) {
                postObj = postObj.addProperty(key, values)
            }
        }

        update.removals?.let { removals ->
            postObj = applyRemovals(postObj, removals)
        }
        postObj = postObj.withoutUntaggedCategory()

        val targetSlug =
            when {
                commands.slug == null || commands.slug == post.slug -> post.slug
                else -> postService.deduplicateSlug(commands.slug, post.slug)
            }

        val updated =
            post.copy(
                slug = targetSlug,
                status = commands.status ?: post.status,
                visibility = commands.visibility ?: post.visibility,
            )

        try {
            postService.updatePost(updated, postObj)
        } catch (e: IllegalArgumentException) {
            return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid published value: ${e.message}")
        }

        val updatedUrl = urlService.generatePostUrl(updated)
        val isPublic = updated.publiclyReachable

        val recordedTargetUids = postSyndicationService.findByPostId(post.id).map { it.targetUid }.toSet()
        val (addedTargetUids, removedTargetUids) = syndicationService.diffTargets(recordedTargetUids, update)
        if (removedTargetUids.isNotEmpty()) {
            syndicationService.retractTargets(post, removedTargetUids, previousUrl)
        }

        when {
            wasPublic && !isPublic -> {
                webmentionService.deactivateWebmentions(previousUrl)
                syndicationService.syndicateDeleted(updated, previousUrl)
            }

            isPublic && !wasPublic -> {
                webmentionService.processWebmentions(updatedUrl, postObj)
                syndicationService.syndicatePublished(updated)
                websubPublisher.publish()
            }

            isPublic && updatedUrl != previousUrl -> {
                webmentionService.processWebmentions(updatedUrl, postObj)
                syndicationService.syndicateRebased(updated, previousUrl)
                websubPublisher.publish()
            }

            isPublic -> {
                webmentionService.processUpdatedWebmentions(updatedUrl, previousTargetUrls, postObj)
                syndicationService.syndicateUpdated(updated, update)
                websubPublisher.publish()
            }
        }

        if (addedTargetUids.isNotEmpty()) {
            if (isPublic) {
                syndicationService.syndicateCreated(updated, addedTargetUids)
            } else {
                syndicationService.retainSyndicationTargets(updated, addedTargetUids)
            }
        }

        return if (targetSlug != post.slug) {
            ApiResponse.Success.Created(urlService.generatePostUrl(updated))
        } else {
            ApiResponse.Success.NoContent
        }
    }

    private fun applyRemovals(
        postObj: Mf2Object,
        removals: MicropubUpdatePayload.Removals,
    ): Mf2Object =
        when (removals) {
            is MicropubUpdatePayload.Removals.All -> {
                removals.properties.fold(postObj) { current, property -> current.deleteProperty(property) }
            }

            is MicropubUpdatePayload.Removals.Many -> {
                removals.properties.entries.fold(postObj) { current, (key, values) ->
                    val remaining = current.getProperty(key).filter { it !in values }
                    if (remaining.isNotEmpty()) current.setProperty(key, remaining) else current.deleteProperty(key)
                }
            }
        }
}
