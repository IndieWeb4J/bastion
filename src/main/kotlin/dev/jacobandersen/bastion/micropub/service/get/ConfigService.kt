package dev.jacobandersen.bastion.micropub.service.get

import dev.jacobandersen.bastion.micropub.syndication.SyndicationConfig
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ConfigService(
    @Value($$"${bastion.public-url}") val publicUrl: String,
    private val syndicationConfig: SyndicationConfig,
) {
    fun getConfig(): ApiResponse<*> =
        ApiResponse.Success.Ok(
            mapOf(
                "media-endpoint" to "${publicUrl.trimEnd('/')}/micropub/media",
                "syndicate-to" to syndicationConfig.syndicationTargets(),
            ),
        )
}
