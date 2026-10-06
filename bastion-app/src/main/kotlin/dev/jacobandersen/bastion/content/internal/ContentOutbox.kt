package dev.jacobandersen.bastion.content.internal

import dev.jacobandersen.bastion.content.Post
import dev.jacobandersen.bastion.content.PostTypeDiscovery
import dev.jacobandersen.bastion.content.url.UrlService
import dev.jacobandersen.content.event.ContentEventSubjects
import dev.jacobandersen.content.event.ContentPostEvent
import dev.jacobandersen.content.event.ContentPostEventType
import dev.jacobandersen.microformats2.firstText
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * Writes content events to a local outbox table in the same transaction as the
 * post write. A separate drainer ([ContentOutboxDrainer]) publishes them to the
 * bus, so an event is never lost between the DB commit and the broker.
 */
@Component
class ContentOutbox(
    private val repository: ContentOutboxRepository,
    private val objectMapper: ObjectMapper,
    private val urlService: UrlService,
) {
    @Transactional
    fun enqueue(
        type: ContentPostEventType,
        post: Post,
        previousUrl: String?,
        syndicationTargets: List<String>? = null,
    ) {
        val event =
            ContentPostEvent(
                eventType = type,
                id = post.id.toString(),
                slug = post.slug,
                url = urlService.generatePostUrl(post),
                previousUrl = previousUrl,
                h = post.h,
                type = post.type,
                status = post.status.name,
                visibility = post.visibility.name,
                deleted = post.deleted,
                categories = PostTypeDiscovery.categories(post.post),
                published = post.post.firstText("published"),
                updated = post.post.firstText("updated"),
                version = post.version,
                post = if (type == ContentPostEventType.DELETED) null else post.post,
                syndicationTargets = syndicationTargets ?: emptyList(),
            )
        repository.save(
            ContentOutboxEntity(
                subject = ContentEventSubjects.subjectFor(type),
                partitionKey = post.id.toString(),
                payload = objectMapper.writeValueAsString(event),
            ),
        )
    }
}
