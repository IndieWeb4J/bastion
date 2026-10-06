package dev.jacobandersen.bastion.content.internal

import dev.jacobandersen.bastion.content.event.ContentEventPublisher
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.content.event.ContentPostEvent
import dev.jacobandersen.content.event.ContentPostEventType
import dev.jacobandersen.content.event.ContentEventSubjects
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Writes content events to a local outbox table in the same transaction as the
 * post write. A separate drainer ([ContentOutboxDrainer]) publishes them to the
 * bus, so an event is never lost between the DB commit and the broker.
 *
 * When no bus is configured the publisher is a no-op and rows are marked
 * published immediately, keeping the local (pre-split) deployment working.
 */
@Component
class ContentOutbox(
    private val repository: ContentOutboxRepository,
    private val publisher: ContentEventPublisher,
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
                url = runCatching { urlService.generatePostUrl(post) }.getOrDefault(""),
                previousUrl = previousUrl,
                h = post.h,
                type = post.type,
                status = post.status.name,
                visibility = post.visibility.name,
                deleted = post.deleted,
                categories = emptyList(),
                published = null,
                updated = null,
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
