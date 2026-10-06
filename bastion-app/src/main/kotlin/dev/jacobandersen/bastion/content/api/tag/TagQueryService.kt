package dev.jacobandersen.bastion.content.api.tag

import dev.jacobandersen.bastion.content.PostRepository
import dev.jacobandersen.bastion.content.PostService
import dev.jacobandersen.bastion.content.api.dto.Pagination
import dev.jacobandersen.bastion.content.api.tag.dto.TagListResponse
import dev.jacobandersen.bastion.content.api.tag.dto.TagResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TagQueryService(
    private val repository: PostRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        limitArg: Int?,
        offsetArg: Int?,
    ): TagListResponse {
        val limit = limitArg ?: 50
        val offset = offsetArg ?: 0

        require(limit in 1..PostService.MAX_PAGE_SIZE) { "limit must be an integer between 1 and ${PostService.MAX_PAGE_SIZE}" }
        require(offset >= 0) { "offset must be a non-negative integer" }
        require(offset % limit == 0) { "offset must be a multiple of limit ($offset % $limit != 0)" }

        val rows = repository.findTagsWithCounts(limit, offset)
        val total = repository.countDistinctTags()

        val tags = rows.map { TagResponse(tag = it.getTag(), count = it.getCnt().toInt()) }

        return TagListResponse(
            tags = tags,
            pagination =
                Pagination(
                    limit = limit,
                    offset = offset,
                    count = tags.size,
                    hasMore = (offset + tags.size) < total,
                ),
        )
    }
}
