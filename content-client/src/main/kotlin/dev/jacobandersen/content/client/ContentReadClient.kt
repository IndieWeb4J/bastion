package dev.jacobandersen.content.client

/**
 * Read access to the content service: fetch a post, list changed posts for
 * reconciliation, and confirm a URL is a public post. Available to any service.
 */
interface ContentReadClient {
    /** Fetches a post by slug, or null when none exists. */
    fun postBySlug(slug: String): PostDto?

    /** Fetches a post by its canonical URL, or null when none exists. */
    fun postByUrl(url: String): PostDto?

    /** Fetches a post by id, or null when none exists. */
    fun postById(id: String): PostDto?

    /**
     * Lists posts changed since an opaque cursor (null = from the beginning),
     * for reconciliation. [nextCursor] is null when there are no more pages.
     */
    fun changedSince(
        cursor: String? = null,
        limit: Int = 100,
    ): ChangedPostsPage

    /** Whether [url] is a currently-public post on this content service. */
    fun isPublicPost(url: String): Boolean =
        postByUrl(url)?.let { !it.deleted && it.status == "PUBLISHED" && it.visibility == "PUBLIC" } == true
}
