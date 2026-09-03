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
}