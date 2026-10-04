package dev.jacobandersen.bastion.post

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Post-type metadata served from q=config and used to validate `subtype`
 * query filters. Bound from an external YAML file (mounted via k8s
 * configmap) imported with spring.config.import; missing/malformed
 * configuration fails fast at startup.
 */
@ConfigurationProperties(prefix = "bastion.post-types")
data class PostTypesConfig(
    val commonProperties: List<String> = emptyList(),
    val postTypes: List<PostTypeDefinition> = emptyList(),
) {
    data class PostTypeDefinition(
        val type: String = "",
        val name: String = "",
        val h: String = "entry",
        val properties: List<String> = emptyList(),
        val requiredProperties: List<String> = emptyList(),
    )
}
