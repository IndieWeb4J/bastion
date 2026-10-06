package dev.jacobandersen.bastion.micropub.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority

class MicropubAuthentication(
    private val raw: String,
    private val token: MicropubToken,
    private var authenticated: Boolean = false,
) : Authentication {
    override fun getAuthorities(): Collection<GrantedAuthority> = token.scope

    override fun getCredentials(): String = raw

    override fun getDetails(): Any = token

    override fun getPrincipal(): Any = token

    override fun isAuthenticated(): Boolean = authenticated

    override fun setAuthenticated(isAuthenticated: Boolean) {
        authenticated = isAuthenticated
    }

    override fun getName(): String = token.me
}
