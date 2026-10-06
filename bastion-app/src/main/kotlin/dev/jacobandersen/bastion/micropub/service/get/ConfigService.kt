package dev.jacobandersen.bastion.micropub.service.get

import dev.jacobandersen.bastion.micropub.syndication.SyndicationConfig
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.PostTypesRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ConfigService(
    @Value($$"${bastion.public-url}") val publicUrl: String,
    private val syndicationConfig: SyndicationConfig,
    private val postTypesRegistry: PostTypesRegistry,
) {
    fun getConfig(): ApiResponse<*> =
        ApiResponse.Success.Ok(
            mapOf(
                "media-endpoint" to "${publicUrl.trimEnd('/')}/micropub/media",
                "syndicate-to" to syndicationConfig.syndicationTargets(),
                "post-types" to postTypesRegistry.postTypesView(),
            ),
        )
}
