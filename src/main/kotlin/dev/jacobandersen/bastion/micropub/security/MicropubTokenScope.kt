package dev.jacobandersen.bastion.micropub.security

import org.springframework.security.core.GrantedAuthority

enum class MicropubTokenScope : GrantedAuthority {
    CREATE,
    UPDATE,
    DELETE,
    UNDELETE,
    MEDIA;

    override fun getAuthority(): String {
        return name
    }

    companion object {
        fun fromString(scope: String): MicropubTokenScope {
            return entries.find { it.name.equals(scope, ignoreCase = true) } ?: throw IllegalArgumentException("Unknown token scope '$scope'")
        }
    }
}