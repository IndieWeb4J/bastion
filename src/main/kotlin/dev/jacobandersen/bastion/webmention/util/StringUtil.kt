package dev.jacobandersen.bastion.webmention.util

internal fun String.unquote(): String = this.trim().let {
    if (it.startsWith("\"") && it.endsWith("\"") && it.length >= 2) {
        it.substring(1, it.length - 1)
    } else {
        it
    }
}
