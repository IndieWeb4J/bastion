package dev.jacobandersen.bastion.sigil

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The subset of Sigil's RFC 7662 introspection response Bastion consumes
 * (IndieAuth adds `me`). All fields other than [active] are absent when the
 * token is inactive.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SigilIntrospection(
    val active: Boolean,
    @JsonProperty("me") val me: String? = null,
    @JsonProperty("client_id") val clientId: String? = null,
    @JsonProperty("scope") val scope: String? = null,
    @JsonProperty("exp") val exp: Long? = null,
    @JsonProperty("iat") val iat: Long? = null,
)
