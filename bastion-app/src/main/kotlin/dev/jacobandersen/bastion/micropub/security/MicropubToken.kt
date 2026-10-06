package dev.jacobandersen.bastion.micropub.security

/**
 * The IndieAuth token identity Micropub accepts: the profile URL it was issued
 * for, the client that requested it, and the granted scopes (as authorities).
 */
data class MicropubToken(
    val me: String,
    val clientId: String,
    val scope: List<MicropubTokenScope>,
)
