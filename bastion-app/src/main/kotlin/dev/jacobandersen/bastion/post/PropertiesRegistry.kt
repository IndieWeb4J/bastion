package dev.jacobandersen.bastion.post

import org.springframework.stereotype.Service

/**
 * Access point for the configured property metadata. Validates the config at
 * startup, including that every property referenced by the post-types config
 * is defined here, and renders the q=properties view.
 */
@Service
class PropertiesRegistry(
    private val config: PropertiesConfig,
    postTypesConfig: PostTypesConfig,
) {
    private val byName: Map<String, PropertiesConfig.PropertyDefinition>

    init {
        require(config.properties.isNotEmpty()) { "bastion.properties.properties must not be empty" }
        val names = config.properties.map { it.name }
        require(names.all { it.isNotBlank() }) { "every property needs a non-blank name" }
        require(names.distinct().size == names.size) { "duplicate property names in config" }
        byName = config.properties.associateBy { it.name }

        val referenced = postTypesConfig.commonProperties + postTypesConfig.postTypes.flatMap { it.properties + it.requiredProperties }
        val missing = referenced.distinct().filterNot { it in byName }
        require(missing.isEmpty()) { "post-types reference undefined properties: $missing" }
    }

    fun view(name: String): Map<String, Any?>? =
        byName[name]?.let { def ->
            buildMap {
                put("name", def.name)
                put("hints", def.hints)
                put("display-name", def.displayName)
                if (def.options != null) put("options", def.options)
                if (def.default != null) put("default", def.default)
            }
        }

    fun allViews(): List<Map<String, Any?>> = config.properties.mapNotNull { view(it.name) }

    fun viewsFor(names: Collection<String>): List<Map<String, Any?>> = names.mapNotNull { view(it) }
}
