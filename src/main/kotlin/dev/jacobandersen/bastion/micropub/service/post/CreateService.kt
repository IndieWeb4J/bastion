package dev.jacobandersen.bastion.micropub.service.post

import com.github.slugify.Slugify
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.mf2.Mf2Object
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.mf2.asMf2Object
import dev.jacobandersen.bastion.micropub.type.mf2.asMf2Value
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.media.FileUploadResult
import dev.jacobandersen.bastion.micropub.media.FileUploadService
import dev.jacobandersen.bastion.micropub.service.MicropubCommandResolver
import dev.jacobandersen.bastion.micropub.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.url.UrlService
import dev.jacobandersen.bastion.util.StringUtil.excerpt
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
) {
    @PreAuthorize("hasAuthority('CREATE')")
    fun create(payload: MicropubPayload, files: MultiValueMap<String, MultipartFile>?): ApiResponse<*> {
        logger.info { "Parsing post payload..." }
        val obj = payload.asMf2Object()

        logger.info { "Resolving post commands..." }
        val commands = commandResolver.resolve { key -> obj.properties[key] }
            ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid command parameters in create")

        obj.properties.keys.filter { MicropubCommand.isCommandProperty(it) }.forEach { obj.deleteProperty(it) }

        logger.info { "Determining post slug..." }
        val slug = postService.deduplicateSlug(commands.slug ?: deriveSlug(obj))
        logger.info { "Using \"$slug\" as slug..." }

        val status = commands.status ?: PostStatus.PUBLISHED
        val visibility = commands.visibility ?: PostVisibility.PUBLIC

        logger.info { "Uploading files (if any)..." }
        files?.entries?.forEach { (_, files) ->
            files.forEach { file ->
                try {
                    uploadFile(obj, file)
                } catch (_: Exception) {
                    return ApiResponse.Error.Unknown(errorDescription = "file upload error")
                }
            }
        }

        logger.info { "Creating post..." }
        val post = try {
            postService.create(slug, status, visibility, deleted = false, post = obj)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Failed to create post: ${e.message}" }
            return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid published value: ${e.message}")
        }
        val url = urlService.generatePostUrl(post)

        logger.info { "Post created: $url" }
        return ApiResponse.Success.Created(url)
    }

    private fun deriveSlug(obj: Mf2Object): String {
        logger.info { "No suggested slug, generating..." }

        return if (obj.hasProperty("name")) {
            logger.info { "Using name property for slug..." }
            slugify.slugify(obj.getFirstProperty("name")!!.toString().excerpt(30))
        } else if (obj.hasProperty("content")) {
            logger.info { "Using content property for slug..." }
            slugify.slugify(obj.getFirstProperty("content")!!.toString().excerpt(30))
        } else {
            logger.info { "No name or content, using UUID..." }
            UUID.randomUUID().toString()
        }
    }

    private fun uploadFile(obj: Mf2Object, file: MultipartFile?) {
        if (file == null) return

        when (val result = fileUploadService.upload(file)) {
            is FileUploadResult.Success -> {
                logger.info { "File uploaded successfully" }
                obj.addProperty(result.name, result.url.asMf2Value())
            }

            is FileUploadResult.Failure -> {
                logger.error(result.error) { "File upload failed" }
                throw result.error
            }
        }
    }
}
