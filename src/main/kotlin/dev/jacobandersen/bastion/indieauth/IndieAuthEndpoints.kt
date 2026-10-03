package dev.jacobandersen.bastion.indieauth

/**
 * The paths of Bastion's own IndieAuth HTTP surface. These are stable API
 * endpoints, not configuration: they are referenced by the controllers and used
 * to build the discovery metadata and the Herald `return_to` URL.
 */
object IndieAuthEndpoints {
    const val AUTHORIZATION = "/indieauth/auth"
    const val CALLBACK = "/indieauth/auth/callback"
    const val TOKEN = "/indieauth/token"
    const val INTROSPECTION = "/indieauth/introspect"
    const val REVOCATION = "/indieauth/revocation"
    const val USERINFO = "/indieauth/userinfo"
    const val PROVIDERS = "/indieauth/providers"
}
