package dev.jacobandersen.bastion.content.internal

import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.post.PostTypeDiscovery
import dev.jacobandersen.bastion.post.PostTypesRegistry
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.content.client.CreatePostCommand
import dev.jacobandersen.content.client.PostDto
import dev.jacobandersen.content.client.UpdatePostCommand
import dev.jacobandersen.content.client.WebmentionCountsDto
import dev.jacobandersen.content.client.WritePostResult
import dev.jacobandersen.content.event.ContentPostEventType
import dev.jacobandersen.mf24j.firstText
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The internal write path into the content store: translates the content-client
 * commands (mf2 + hints) into post mutations, assigning the canonical slug,
 * applying PTD/derived columns, and enqueuing the outgoing event in the same
 * transaction. Bastion is the authority for slug, timestamps and version.
 */
@Service
class InternalContentService(
    private val postService: PostService,
    private val urlService: UrlService,
    private val postTypesRegistry: PostTypesRegistry,
    private val outbox: ContentOutbox,
) {
    @Transactional
    fun create(command: CreatePostCommand): WritePostResult {
        val status = command.status?.let(PostStatus::fromString) ?: PostStatus.PUBLISHED
        val visibility = command.visibility?.let(PostVisibility::fromString) ?: PostVisibility.PUBLIC
        val slug = postService.deduplicateSlug(command.slugHint ?: UUID.randomUUID().toString())
        val post = postService.create(slug, status, visibility, deleted = false, post = command.post)
        outbox.enqueue(ContentPostEventType.CREATED, post, previousUrl = null, syndicationTargets = command.syndicationTargets)
        return result(post)
    }

    @Transactional
    fun update(
        id: UUID,
        command: UpdatePostCommand,
    ): WritePostResult {
        val current = postService.findById(id) ?: throw ContentNotFoundException(id)
        val previousUrl = urlService.generatePostUrl(current)
        val status = command.status?.let(PostStatus::fromString) ?: current.status
        val visibility = command.visibility?.let(PostVisibility::fromString) ?: current.visibility
        val slug = postService.deduplicateSlug(command.slugHint ?: current.slug, current.slug)
        val updated = postService.updatePost(current.copy(slug = slug, status = status, visibility = visibility), command.post)
        outbox.enqueue(ContentPostEventType.UPDATED, updated, previousUrl = previousUrl, syndicationTargets = command.syndicationTargets)
        return result(updated)
    }

    @Transactional
    fun setDeleted(
        id: UUID,
        deleted: Boolean,
    ): WritePostResult {
        val current = postService.findById(id) ?: throw ContentNotFoundException(id)
        val url = urlService.generatePostUrl(current)
        val updated = postService.updatePost(current.copy(deleted = deleted), current.post)
        val type = if (deleted) ContentPostEventType.DELETED else ContentPostEventType.UPDATED
        outbox.enqueue(type, updated, previousUrl = if (!deleted) url else null)
        return result(updated)
    }

    @Transactional(readOnly = true)
    fun toDto(post: Post): PostDto =
        PostDto(
            id = post.id.toString(),
            slug = post.slug,
            url = urlService.generatePostUrl(post),
            h = post.h,
            type = post.type,
            status = post.status.name,
            visibility = post.visibility.name,
            deleted = post.deleted,
            categories = PostTypeDiscovery.categories(post.post),
            published = post.post.firstText("published"),
            updated = post.post.firstText("updated"),
            version = post.version,
            post = post.post,
            webmentionCounts = WebmentionCountsDto(),
        )

    private fun result(post: Post): WritePostResult =
        WritePostResult(
            id = post.id.toString(),
            slug = post.slug,
            url = urlService.generatePostUrl(post),
            version = post.version,
        )

    fun isKnownType(type: String): Boolean = postTypesRegistry.isKnownType(type)
}

class ContentNotFoundException(
    id: UUID,
) : RuntimeException("Content not found: $id")
