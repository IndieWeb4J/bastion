package dev.jacobandersen.content.client

import dev.jacobandersen.mf24j.Mf2Object

/**
 * The command Bastion accepts at `POST /internal/posts` to create a post. The
 * payload is the canonical mf2 document plus command hints; Bastion owns
 * post-type discovery, slug dedup and timestamp stamping.
 */
data class CreatePostCommand(
    /** The post document. The write boundary is mf2. */
    val post: Mf2Object,
    /** A slug hint (e.g. from `mp-slug`); Bastion dedups and may ignore it. */
    val slugHint: String? = null,
    /** `published` / `draft`; null lets Bastion default. */
    val status: String? = null,
    /** `public` / `unlisted` / `private`; null lets Bastion default. */
    val visibility: String? = null,
    /**
     * A `published` timestamp hint (RFC 3339), honored when valid (e.g. backup
     * imports), otherwise Bastion stamps now.
     */
    val publishedHint: String? = null,
    /** Requested syndication target uids (`mp-syndicate-to`), recorded desired state. */
    val syndicationTargets: List<String> = emptyList(),
)

/**
 * The command Bastion accepts at `PATCH /internal/posts/{id}` to update a post.
 * Mirrors the Micropub update semantics (replace/add/delete property maps).
 */
data class UpdatePostCommand(
    /** The full resulting post document (already merged by the caller). */
    val post: Mf2Object,
    val slugHint: String? = null,
    val status: String? = null,
    val visibility: String? = null,
    /** An explicit `updated` hint, rare; null lets Bastion stamp now. */
    val updatedHint: String? = null,
    val syndicationTargets: List<String>? = null,
)

/** The result of a create/update/delete: the canonical identity of the post. */
data class WritePostResult(
    val id: String,
    val slug: String,
    val url: String,
    /** Monotonic per-post version used for event ordering. */
    val version: Long,
)

/** A media upload result from `POST /internal/media`. */
data class MediaUploadResult(
    val url: String,
)

/** A read model of a post as Bastion stores and serves it. */
data class PostDto(
    val id: String,
    val slug: String,
    val url: String,
    val h: String,
    val type: String? = null,
    val status: String,
    val visibility: String,
    val deleted: Boolean,
    val categories: List<String>,
    val published: String? = null,
    val updated: String? = null,
    val version: Long,
    /** The canonical mf2 document. */
    val post: Mf2Object,
    /** Requested syndication target uids (desired state, from `mp-syndicate-to`). */
    val desiredSyndicationTargets: List<String> = emptyList(),
    val webmentionCounts: WebmentionCountsDto = WebmentionCountsDto(),
    val syndications: List<SyndicationDto> = emptyList(),
)

data class WebmentionCountsDto(
    val total: Int = 0,
    val reply: Int = 0,
    val like: Int = 0,
    val repost: Int = 0,
    val bookmark: Int = 0,
    val rsvp: Int = 0,
    val mention: Int = 0,
)

data class SyndicationDto(
    val uid: String,
    val name: String,
    val url: String,
)

/**
 * A page of posts changed since a cursor, for consumer reconciliation. [nextCursor]
 * is opaque and monotonically increasing.
 */
data class ChangedPostsPage(
    val posts: List<PostDto>,
    val nextCursor: String?,
)
