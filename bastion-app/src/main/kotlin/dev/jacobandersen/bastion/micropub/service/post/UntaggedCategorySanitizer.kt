package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.micropub.type.PostTagFilter
import dev.jacobandersen.bastion.micropub.type.req.MicropubUpdatePayload
import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import dev.jacobandersen.mf24j.plainTextOrNull
import java.util.Locale

private const val CATEGORY_PROPERTY = "category"

/**
 * Removes the reserved `none` value from a post's categories.
 *
 * The value is matched after trimming and case normalization because public
 * tag filtering is case-insensitive. Other category value forms are retained.
 */
internal fun Mf2Object.withoutUntaggedCategory(): Mf2Object {
    val categories = getProperty(CATEGORY_PROPERTY)
    if (categories.isEmpty()) return this

    val filtered = categories.filterNot(::isUntaggedCategory)
    return if (filtered.size == categories.size) this else setProperty(CATEGORY_PROPERTY, filtered)
}

/** Applies the category sanitization to update additions and replacements. */
internal fun MicropubUpdatePayload.withoutUntaggedCategories(): MicropubUpdatePayload =
    copy(
        replacements = replacements?.withoutUntaggedCategoryValues(),
        additions = additions?.withoutUntaggedCategoryValues(),
    )

private fun Map<String, List<Mf2Value>>.withoutUntaggedCategoryValues(): Map<String, List<Mf2Value>> =
    mapValues { (property, values) ->
        if (property == CATEGORY_PROPERTY) values.filterNot(::isUntaggedCategory) else values
    }

private fun isUntaggedCategory(value: Mf2Value): Boolean = value.plainTextOrNull?.trim()?.lowercase(Locale.ROOT) == PostTagFilter.UNTAGGED
