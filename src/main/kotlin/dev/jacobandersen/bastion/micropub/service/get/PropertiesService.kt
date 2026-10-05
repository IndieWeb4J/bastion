package dev.jacobandersen.bastion.micropub.service.get

import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.PostTypesConfig
import dev.jacobandersen.bastion.post.PostTypesRegistry
import dev.jacobandersen.bastion.post.PropertiesRegistry
import org.springframework.stereotype.Service

@Service
class PropertiesService(
    private val propertiesRegistry: PropertiesRegistry,
    private val postTypesRegistry: PostTypesRegistry,
    private val postTypesConfig: PostTypesConfig,
) {
    fun getProperties(postType: String?): ApiResponse<*> {
        if (postType == null) {
            return ApiResponse.Success.Ok(mapOf("properties" to propertiesRegistry.allViews()))
        }

        val value = postType.lowercase()
        if (!postTypesRegistry.isKnownType(value)) {
            return ApiResponse.Error.InvalidRequest(errorDescription = "unknown post type: $postType")
        }

        val def = postTypesConfig.postTypes.first { it.type == value }
        val names = postTypesConfig.commonProperties + def.properties
        return ApiResponse.Success.Ok(mapOf("properties" to propertiesRegistry.viewsFor(names)))
    }
}
