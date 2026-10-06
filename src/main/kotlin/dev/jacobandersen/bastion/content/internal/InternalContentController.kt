package dev.jacobandersen.bastion.content.internal

import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.media.FileUploadResult
import dev.jacobandersen.bastion.micropub.media.FileUploadService
import dev.jacobandersen.content.client.ChangedPostsPage
import dev.jacobandersen.content.client.CreatePostCommand
import dev.jacobandersen.content.client.MediaUploadResult
import dev.jacobandersen.content.client.PostDto
import dev.jacobandersen.content.client.UpdatePostCommand
import dev.jacobandersen.content.client.WritePostResult
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Internal content API: the synchronous command path Forge writes through, the
 * read path Beacon/Conduit use for verification and reconciliation, and the
 * media command Forge forwards uploads to. Gated by the service-token filter
 * (`InternalSecurityConfig`); the write boundary is mf2 ([CreatePostCommand]).
 */
@RestController
@RequestMapping("/internal")
class InternalContentController(
    private val service: InternalContentService,
    private val postService: PostService,
    private val fileUploadService: FileUploadService,
) {
    // ------------------------------------------------------------------- write

    @PostMapping("/posts", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @RequestBody command: CreatePostCommand,
    ): WritePostResult = service.create(command)

    @PatchMapping("/posts/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @PathVariable id: UUID,
        @RequestBody command: UpdatePostCommand,
    ): WritePostResult = service.update(id, command)

    @PostMapping("/posts/{id}/delete")
    fun delete(
        @PathVariable id: UUID,
    ): WritePostResult = service.setDeleted(id, deleted = true)

    @PostMapping("/posts/{id}/undelete")
    fun undelete(
        @PathVariable id: UUID,
    ): WritePostResult = service.setDeleted(id, deleted = false)

    // -------------------------------------------------------------------- read

    @GetMapping("/posts")
    fun lookup(
        @RequestParam(required = false) slug: String?,
        @RequestParam(required = false) url: String?,
    ): ResponseEntity<PostDto> {
        val key = slug ?: url?.substringAfterLast('/') ?: return ResponseEntity.badRequest().build()
        val post = postService.findBySlug(key) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(service.toDto(post))
    }

    @GetMapping("/posts/changed")
    fun changed(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "100") limit: Int,
    ): ChangedPostsPage {
        val (items, nextCursor) = postService.changedSince(cursor, limit)
        return ChangedPostsPage(posts = items.map { service.toDto(it.first) }, nextCursor = nextCursor)
    }

    @GetMapping("/posts/{id}")
    fun byId(
        @PathVariable id: UUID,
    ): ResponseEntity<PostDto> {
        val post = postService.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(service.toDto(post))
    }

    // ------------------------------------------------------------------- media

    @PostMapping("/media", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun media(
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Any> {
        if (file.isEmpty) return ResponseEntity.badRequest().body(mapOf("error" to "uploaded file is empty"))
        return when (val result = fileUploadService.upload(file)) {
            is FileUploadResult.Success -> {
                ResponseEntity.status(HttpStatus.CREATED).body(MediaUploadResult(url = result.url))
            }

            is FileUploadResult.Failure -> {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "media upload failed"))
            }
        }
    }
}
