package dev.jacobandersen.bastion.micropub.type.req

import dev.jacobandersen.bastion.microformats2.Mf2Value

data class MicropubUpdatePayload(
    val url: String,
    val replacements: Map<String, List<Mf2Value>>?,
    val additions: Map<String, List<Mf2Value>>?,
    val removals: Removals?,
) {
    sealed interface Removals {
        data class Many(val properties: Map<String, List<Mf2Value>>) : Removals
        data class All(val properties: List<String>) : Removals
    }
}
