package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.req.MicropubUpdatePayload
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.service.MicropubCommandResolver
import dev.jacobandersen.bastion.micropub.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.url.UrlService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class UpdateService(
    val urlService: UrlService,
    private val postService: PostService,
    private val commandResolver: MicropubCommandResolver,
) {
    @PreAuthorize("hasAuthority('UPDATE')")
    fun update(payload: MicropubPayload): ApiResponse<*> {
        val update = try {
            payload.asUpdatePayload()
        } catch (e: Exception) {
            return ApiResponse.Error.InvalidRequest(errorDescription = "Failed to parse payload as update: ${e.message}")
        }

        if (update.replacements == null && update.additions == null && update.removals == null) {
            return ApiResponse.Error.InvalidRequest(errorDescription = "Update payload must include at least one of replace, add, or delete")
        }

        val slug = urlService.extractPostSlug(update.url)
            ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid URL for this Bastion instance: ${update.url}")

        val post = postService.findBySlug(slug)
            ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Post not found for URL: ${update.url}")

        val commands = commandResolver.resolve { key ->
            update.replacements?.replacements?.get(key) ?: update.additions?.additions?.get(key)
        } ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid command parameters in update")

        val postObj = post.post

        update.replacements?.replacements?.forEach { (key, values) ->
            if (!MicropubCommand.isCommandProperty(key)) {
                if (values.isEmpty()) {
                    postObj.deleteProperty(key)
                } else {
                    postObj.setProperty(key, values)
                }
            }
        }

        update.additions?.additions?.forEach { (key, values) ->
            if (!MicropubCommand.isCommandProperty(key)) {
                postObj.addProperty(key, values)
            }
        }

        update.removals?.let { removals ->
            when (removals) {
                is MicropubUpdatePayload.Removals.All -> {
                    removals.properties.forEach { removal ->
                        postObj.deleteProperty(removal)
                    }
                }

                is MicropubUpdatePayload.Removals.Many -> {
                    removals.properties.forEach { (key, values) ->
                        val diff = postObj.getProperty(key).filter { !values.contains(it) }
                        if (diff.isNotEmpty()) {
                            postObj.setProperty(key, diff)
                        } else {
                            postObj.deleteProperty(key)
                        }
                    }
                }
            }
        }

        val targetSlug = when {
            commands.slug == null || commands.slug == post.slug -> post.slug
            else -> postService.deduplicateSlug(commands.slug, post.slug)
        }

        val updated = post.copy(
            slug = targetSlug,
            status = commands.status ?: post.status,
            visibility = commands.visibility ?: post.visibility,
        )

        try {
            postService.updatePost(updated, postObj)
        } catch (e: IllegalArgumentException) {
            return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid published value: ${e.message}")
        }

        return if (targetSlug != post.slug) {
            ApiResponse.Success.Created(urlService.generatePostUrl(updated))
        } else {
            ApiResponse.Success.NoContent
        }
    }
}
