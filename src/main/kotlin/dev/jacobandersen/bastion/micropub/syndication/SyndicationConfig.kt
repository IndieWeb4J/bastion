package dev.jacobandersen.bastion.micropub.syndication

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bastion.micropub.syndication")
data class SyndicationConfig(
    val targets: List<Target> = emptyList(),
) {
    /**
     * A downstream micropub server (such as Bridgy) that Bastion can syndicate
     * to. Each target advertises a stable [uid] (referenced by `mp-syndicate-to`),
     * a display [name], the downstream micropub [endpoint], an optional bearer
     * [token], and the [actions] it supports.
     */
    data class Target(
        val uid: String,
        val name: String,
        val endpoint: String,
        val token: String? = null,
        val actions: Set<SyndicationAction> = setOf(SyndicationAction.CREATE, SyndicationAction.DELETE),
    ) {
        fun supports(action: SyndicationAction): Boolean = action in actions
    }

    fun targetByUid(uid: String): Target? = targets.firstOrNull { it.uid == uid }

    fun syndicationTargets(): List<Map<String, String>> = targets.map { mapOf("uid" to it.uid, "name" to it.name) }

    fun targetsSupporting(
        uids: Collection<String>,
        action: SyndicationAction,
    ): List<Target> =
        uids
            .distinct()
            .mapNotNull { uid -> targetByUid(uid)?.takeIf { it.supports(action) } }
}
