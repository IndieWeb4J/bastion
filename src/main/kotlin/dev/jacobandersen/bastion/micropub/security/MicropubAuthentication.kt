package dev.jacobandersen.bastion.micropub.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority

class MicropubAuthentication(private val raw: String, private val token: MicropubToken, private var authenticated: Boolean = false) : Authentication {
    override fun getAuthorities(): Collection<GrantedAuthority> {
        return token.scope
    }

    override fun getCredentials(): String {
        return raw
    }

    override fun getDetails(): Any {
        return token
    }

    override fun getPrincipal(): Any {
        return token
    }

    override fun isAuthenticated(): Boolean {
        return authenticated
    }

    override fun setAuthenticated(isAuthenticated: Boolean) {
        authenticated = isAuthenticated
    }

    override fun getName(): String {
        return token.me
    }
}