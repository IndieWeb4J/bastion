package dev.jacobandersen.bastion.micropub.service

import com.github.slugify.Slugify
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.micropub.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import org.springframework.stereotype.Service

sealed interface MicropubCommandValue {
    data class Present(
        val value: String,
    ) : MicropubCommandValue

    data object Absent : MicropubCommandValue

    data object Invalid : MicropubCommandValue
}

data class PostCommands(
    val slug: String?,
    val status: PostStatus?,
    val visibility: PostVisibility?,
)

@Service
class MicropubCommandResolver(
    private val slugify: Slugify,
) {
    fun value(values: List<Mf2Value>?): MicropubCommandValue {
        if (values == null) return MicropubCommandValue.Absent
        if (values.size != 1) return MicropubCommandValue.Invalid

        val raw = values.single() as? Mf2Value.String ?: return MicropubCommandValue.Invalid
        return if (raw.value.isBlank()) MicropubCommandValue.Invalid else MicropubCommandValue.Present(raw.value)
    }

    fun status(raw: String): PostStatus = PostStatus.fromString(raw)

    fun visibility(raw: String): PostVisibility = PostVisibility.fromString(raw)

    fun resolve(lookup: (String) -> List<Mf2Value>?): PostCommands? {
        val slug =
            when (val command = value(lookup(MicropubCommand.MP_SLUG))) {
                is MicropubCommandValue.Absent -> null
                is MicropubCommandValue.Invalid -> return null
                is MicropubCommandValue.Present -> slugify.slugify(command.value).takeIf { it.isNotBlank() } ?: return null
            }

        val status =
            when (val command = value(lookup(MicropubCommand.POST_STATUS))) {
                is MicropubCommandValue.Absent -> {
                    null
                }

                is MicropubCommandValue.Invalid -> {
                    return null
                }

                is MicropubCommandValue.Present -> {
                    status(command.value).takeUnless { it == PostStatus.UNKNOWN } ?: return null
                }
            }

        val visibility =
            when (val command = value(lookup(MicropubCommand.VISIBILITY))) {
                is MicropubCommandValue.Absent -> {
                    null
                }

                is MicropubCommandValue.Invalid -> {
                    return null
                }

                is MicropubCommandValue.Present -> {
                    visibility(command.value).takeUnless { it == PostVisibility.UNKNOWN } ?: return null
                }
            }

        return PostCommands(slug, status, visibility)
    }
}
