package dev.jacobandersen.content.event

import dev.jacobandersen.microformats2.Mf2Object

/**
 * The envelope every event carries on the bus. Producer-owned schemas evolve
 * additively; [schemaVersion] allows consumers to branch on shape.
 */
data class EventEnvelope<T>(
    val eventId: String,
    val type: String,
    val schemaVersion: Int = 1,
    /** ISO-8601 timestamp of when the fact occurred. */
    val occurredAt: String,
    /** The producing service, e.g. "bastion". */
    val producer: String,
    /** Ordering key; for content events this is the post id. */
    val partitionKey: String? = null,
    val payload: T,
)

/**
 * Bastion -> content events (producer: content service). One event per post
 * change, carrying the full post so consumers need no callback. Consumers guard
 * on [ContentPostEvent.version] (monotonic per post).
 */
data class ContentPostEvent(
    val eventType: ContentPostEventType,
    val id: String,
    val slug: String,
    val url: String,
    val previousUrl: String? = null,
    val h: String,
    val type: String? = null,
    val status: String,
    val visibility: String,
    val deleted: Boolean,
    val categories: List<String>,
    val published: String? = null,
    val updated: String? = null,
    /** Monotonic version, bumped on every change. */
    val version: Long,
    /** The canonical mf2 document (fat event). */
    val post: Mf2Object? = null,
    /** Desired syndication target uids at the time of the change. */
    val syndicationTargets: List<String> = emptyList(),
)

enum class ContentPostEventType {
    CREATED,
    UPDATED,
    DELETED,
}

/** The NATS subjects and stream names for content and distribution events. */
object ContentEventSubjects {
    const val STREAM = "CONTENT"
    const val CREATED = "content.post.created"
    const val UPDATED = "content.post.updated"
    const val DELETED = "content.post.deleted"

    fun subjectFor(type: ContentPostEventType): String =
        when (type) {
            ContentPostEventType.CREATED -> CREATED
            ContentPostEventType.UPDATED -> UPDATED
            ContentPostEventType.DELETED -> DELETED
        }
}
