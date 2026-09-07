package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostType
import dev.jacobandersen.bastion.url.UrlService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class PostQueryResolver(
    private val queryService: PostQueryService,
    private val urlService: UrlService,
    private val postService: PostService,
) {
    @QueryMapping
    fun posts(
        @Argument types: List<PostType>?,
        @Argument limit: Int?,
        @Argument offset: Int?,
    ): List<Post> {
        return queryService.feed(types, limit, offset)
    }

    @QueryMapping
    fun post(@Argument slug: String?, @Argument url: String?): Any? {
        return when (val result = queryService.post(slug, url)) {
            is PostLookupResult.Found -> result.post
            is PostLookupResult.Gone -> PostGone(result.slug, result.url, result.published)
            null -> null
        }
    }

    @SchemaMapping
    fun id(post: Post): String = post.id.toString()

    @SchemaMapping
    fun url(post: Post): String? = runCatching { urlService.generatePostUrl(post) }.getOrNull()

    @SchemaMapping
    fun published(post: Post): String? = text(post, "published")

    @SchemaMapping
    fun updated(post: Post): String? = text(post, "updated")

    @SchemaMapping
    fun name(post: Post): String? = text(post, "name")

    @SchemaMapping
    fun summary(post: Post): String? = text(post, "summary")

    @SchemaMapping
    fun content(post: Post): String? = text(post, "content")

    @SchemaMapping
    fun contentHtml(post: Post): String? = Mf2Graphql.firstHtml(post.post, "content")

    @SchemaMapping
    fun category(post: Post): List<String> = Mf2Graphql.strings(post.post, "category")

    @SchemaMapping
    fun properties(post: Post, @Argument names: List<String>?): Map<String, List<Any?>> {
        val requested = postService.filterPostFields(post, names?.toTypedArray()).post
        return Mf2Graphql.normalizeProperties(requested)
    }

    private fun text(post: Post, key: String): String? = Mf2Graphql.firstText(post.post, key)
}
