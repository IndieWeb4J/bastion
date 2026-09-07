package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmention
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.MENTION
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

/**
 * GraphQL accessors exposing the verified webmentions received for a post and
 * per-post counts by interaction type. Only VERIFIED webmentions are shown.
 */
@Controller
class WebmentionQueryResolver(
    private val webmentionService: ReceivedWebmentionService,
) {
    @SchemaMapping(typeName = "Post", field = "webmentions")
    fun webmentions(post: Post): List<ReceivedWebmention> {
        return webmentionService.verifiedByPost(post.id).sortedBy { it.firstSeenAt }
    }

    @BatchMapping(typeName = "Post", field = "webmentionCounts")
    fun webmentionCounts(posts: List<Post>): Map<Post, WebmentionCounts> {
        val byPost = webmentionService.verifiedByPostIds(posts.map { it.id }).groupBy { it.postId }
        return posts.associateWith { post ->
            val counts = byPost[post.id]
                ?.groupingBy { it.interaction ?: MENTION }
                ?.eachCount()
                ?: emptyMap()
            WebmentionCounts.of(counts)
        }
    }

    @SchemaMapping(typeName = "Webmention", field = "interaction")
    fun interaction(webmention: ReceivedWebmention): WebmentionInteraction {
        return webmention.interaction ?: MENTION
    }

    @SchemaMapping(typeName = "Webmention", field = "firstSeenAt")
    fun firstSeenAt(webmention: ReceivedWebmention): String = webmention.firstSeenAt.toString()

    @SchemaMapping(typeName = "Webmention", field = "verifiedAt")
    fun verifiedAt(webmention: ReceivedWebmention): String? = webmention.verifiedAt?.toString()
}
