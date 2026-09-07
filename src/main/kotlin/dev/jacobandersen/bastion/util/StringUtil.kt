package dev.jacobandersen.bastion.util

object StringUtil {
    /**
     * Generate an excerpt of the given String. If `maxLen > len`,
     * then just uses len.
     */
    fun String.excerpt(maxLen: Int, addEllipsis: Boolean = false): String {
        val len = Math.clamp(maxLen.toLong(), 0, this.length)
        return this.substring(0, len) + (if (addEllipsis) "..." else "")
    }

    /** Strips a surrounding pair of double quotes, when present. */
    internal fun String.unquote(): String = this.trim().let {
        if (it.startsWith("\"") && it.endsWith("\"") && it.length >= 2) {
            it.substring(1, it.length - 1)
        } else {
            it
        }
    }
}
