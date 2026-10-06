package dev.jacobandersen.bastion.post

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Property display metadata served from q=properties. Bound from an external
 * YAML file (mounted via k8s configmap) imported with spring.config.import;
 * missing/malformed configuration fails fast at startup.
 */
@ConfigurationProperties(prefix = "bastion.properties")
data class PropertiesConfig(
    val properties: List<PropertyDefinition> = emptyList(),
) {
    data class PropertyDefinition(
        val name: String = "",
        val displayName: String = "",
        val hints: List<String> = emptyList(),
        val options: List<String>? = null,
        val default: String? = null,
    )
}
