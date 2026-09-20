package dev.jacobandersen.bastion.micropub.security

import org.springframework.security.core.GrantedAuthority

enum class MicropubTokenScope : GrantedAuthority {
    CREATE,
    UPDATE,
    DELETE,
    UNDELETE,
    MEDIA,
    ;

    override fun getAuthority(): String = name

    companion object {
        fun fromString(scope: String): MicropubTokenScope =
            fromStringOrNull(scope) ?: throw IllegalArgumentException("Unknown token scope '$scope'")

        fun fromStringOrNull(scope: String): MicropubTokenScope? = entries.find { it.name.equals(scope, ignoreCase = true) }
    }
}
