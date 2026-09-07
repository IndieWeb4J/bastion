package dev.jacobandersen.bastion.micropub.service.post

import com.github.slugify.Slugify
import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.microformats2.firstText
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.media.FileUploadResult
import dev.jacobandersen.bastion.micropub.media.FileUploadService
import dev.jacobandersen.bastion.micropub.service.MicropubCommandResolver
import dev.jacobandersen.bastion.micropub.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.util.StringUtil.excerpt
import dev.jacobandersen.bastion.webmention.service.WebmentionService
import dev.jacobandersen.bastion.websub.service.WebsubPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class CreateService(
    private val slugify: Slugify,
    private val postService: PostService,
    private val fileUploadService: FileUploadService,
    private val urlService: UrlService,
    private val commandResolver: MicropubCommandResolver,
    private val webmentionService: WebmentionService,
    private val websubPublisher: WebsubPublisher,
) {
    @PreAuthorize("hasAuthority('CREATE')")
    fun create(
        payload: MicropubPayload,
        files: MultiValueMap<String, MultipartFile>?,
    ): ApiResponse<*> {
        logger.info { "Parsing post payload..." }
        var obj = payload.asMf2Object()

        logger.info { "Resolving post commands..." }
        val commands =
            commandResolver.resolve { key -> obj.properties[key] }
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid command parameters in create")

        obj = obj.copy(properties = obj.properties.filterKeys { !MicropubCommand.isCommandProperty(it) })

        logger.info { "Determining post slug..." }
        val slug = postService.deduplicateSlug(commands.slug ?: deriveSlug(obj))
        logger.info { "Using \"$slug\" as slug..." }

        val status = commands.status ?: PostStatus.PUBLISHED
        val visibility = commands.visibility ?: PostVisibility.PUBLIC

        logger.info { "Uploading files (if any)..." }
        files?.entries?.forEach { (_, fileValues) ->
            fileValues.forEach { file ->
                try {
                    obj = uploadFile(obj, file)
                } catch (_: Exception) {
                    return ApiResponse.Error.Unknown(errorDescription = "file upload error")
                }
            }
        }

        logger.info { "Creating post..." }
        val post =
            try {
                postService.create(slug, status, visibility, deleted = false, post = obj)
            } catch (e: IllegalArgumentException) {
                logger.warn { "Failed to create post: ${e.message}" }
                return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid published value: ${e.message}")
            }
        val url = urlService.generatePostUrl(post)

        logger.info { "Dispatching webmention and WebSub processing..." }
        if (post.publiclyReachable) {
            webmentionService.processWebmentions(url, post.post)
            websubPublisher.publish()
        }

        logger.info { "Post created: $url" }
        return ApiResponse.Success.Created(url)
    }

    private fun deriveSlug(obj: Mf2Object): String {
        logger.info { "No suggested slug, generating..." }

        val name = obj.firstText("name")
        if (name != null) {
            logger.info { "Using name property for slug..." }
            return slugify.slugify(name.excerpt(MAX_SLUG_SOURCE_LENGTH))
        }

        val content = obj.firstText("content")
        if (content != null) {
            logger.info { "Using content property for slug..." }
            return slugify.slugify(content.excerpt(MAX_SLUG_SOURCE_LENGTH))
        }

        logger.info { "No name or content, using UUID..." }
        return UUID.randomUUID().toString()
    }

    private fun uploadFile(
        obj: Mf2Object,
        file: MultipartFile?,
    ): Mf2Object {
        if (file == null) return obj

        return when (val result = fileUploadService.upload(file)) {
            is FileUploadResult.Success -> {
                logger.info { "File uploaded successfully" }
                obj.addProperty(result.name, Mf2Value.String(result.url))
            }

            is FileUploadResult.Failure -> {
                logger.error(result.error) { "File upload failed" }
                throw result.error
            }
        }
    }

    companion object {
        private const val MAX_SLUG_SOURCE_LENGTH = 30
    }
}
