package dev.jacobandersen.bastion.post

import org.springframework.stereotype.Service

/**
 * Single access point for the configured post types: validates the config
 * once at startup, exposes the known type names for query validation, and
 * renders the q=config `post-types` view with common properties merged in.
 */
@Service
class PostTypesRegistry(
    private val config: PostTypesConfig,
) {
    init {
        require(config.postTypes.isNotEmpty()) { "bastion.post-types.post-types must not be empty" }
        val names = config.postTypes.map { it.type }
        require(names.all { it.isNotBlank() }) { "every post type needs a non-blank type" }
        require(names.distinct().size == names.size) { "duplicate post type names in config" }
    }

    fun knownTypes(): Set<String> = config.postTypes.map { it.type }.toSet()

    fun isKnownType(type: String): Boolean = type in knownTypes()

    fun postTypesView(): List<Map<String, Any?>> =
        config.postTypes.map { def ->
            mapOf(
                "type" to def.type,
                "name" to def.name,
                "h" to def.h,
                "properties" to (config.commonProperties + def.properties),
                "required-properties" to def.requiredProperties,
            )
        }
}
