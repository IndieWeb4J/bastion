package dev.jacobandersen.bastion.micropub.type.req

import dev.jacobandersen.bastion.microformats2.Mf2Value

class MicropubUpdatePayload(
    val url: String,
    val replacements: Replacements?,
    val additions: Additions?,
    val removals: Removals?
) {
    data class Additions(val additions: Map<String, List<Mf2Value>>)

    data class Replacements(val replacements: Map<String, List<Mf2Value>>)

    sealed interface Removals {
        data class Many(val properties: Map<String, List<Mf2Value>>) : Removals
        data class All(val properties: List<String>) : Removals
    }
}