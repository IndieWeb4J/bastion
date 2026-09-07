package dev.jacobandersen.bastion.micropub.type

object MicropubCommand {
    const val MP_PREFIX = "mp-"
    const val MP_SLUG = "mp-slug"
    const val POST_STATUS = "post-status"
    const val VISIBILITY = "visibility"

    fun isCommandProperty(key: String): Boolean = key.startsWith(MP_PREFIX) || key == POST_STATUS || key == VISIBILITY
}
