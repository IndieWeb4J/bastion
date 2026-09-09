package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.syndication.SyndicationService
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.service.WebmentionService
import dev.jacobandersen.bastion.websub.service.WebsubPublisher
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class DeleteService(
    val urlService: UrlService,
    val postService: PostService,
    private val webmentionService: WebmentionService,
    private val websubPublisher: WebsubPublisher,
    private val syndicationService: SyndicationService,
) {
    @PreAuthorize("hasAuthority('DELETE')")
    fun delete(payload: MicropubPayload): ApiResponse<*> = commonDelete(payload, true)

    @PreAuthorize("hasAuthority('UNDELETE')")
    fun undelete(payload: MicropubPayload): ApiResponse<*> = commonDelete(payload, false)

    private fun commonDelete(
        payload: MicropubPayload,
        delete: Boolean,
    ): ApiResponse<*> {
        val url =
            try {
                payload.getUrl()
            } catch (e: Exception) {
                return ApiResponse.Error.InvalidRequest(errorDescription = "URL missing: ${e.message}")
            }

        val slug =
            urlService.extractPostSlug(url)
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid URL for this Bastion instance: $url")

        val post =
            postService.findBySlug(slug)
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Post not found for URL: $url")

        val postUrl = urlService.generatePostUrl(post)
        val isPublic = post.isPublicContent
        val wasDeleted = post.deleted

        try {
            postService.updatePost(post.copy(deleted = delete), post.post)
        } catch (e: IllegalArgumentException) {
            return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid published value: ${e.message}")
        }

        when {
            delete && !wasDeleted && isPublic -> {
                webmentionService.processDeletedWebmentions(postUrl)
                websubPublisher.publish()
            }

            !delete && wasDeleted && isPublic -> {
                webmentionService.processWebmentions(postUrl, post.post)
                websubPublisher.publish()
            }
        }

        if (delete && !wasDeleted) {
            syndicationService.syndicateDeleted(post)
        }
        if (!delete && wasDeleted && isPublic) {
            syndicationService.syndicateUndeleted(post)
        }

        return ApiResponse.Success.NoContent
    }
}
