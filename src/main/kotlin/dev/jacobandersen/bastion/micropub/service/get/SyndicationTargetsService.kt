package dev.jacobandersen.bastion.micropub.service.get

import dev.jacobandersen.bastion.micropub.syndication.SyndicationConfig
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import org.springframework.stereotype.Service

@Service
class SyndicationTargetsService(
    private val syndicationConfig: SyndicationConfig,
) {
    fun getSyndicationTargets(): ApiResponse<*> =
        ApiResponse.Success.Ok(
            mapOf(
                "syndicate-to" to syndicationConfig.syndicationTargets(),
            ),
        )
}
