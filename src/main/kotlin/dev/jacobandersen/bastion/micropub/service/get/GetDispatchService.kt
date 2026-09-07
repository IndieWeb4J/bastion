package dev.jacobandersen.bastion.micropub.service.get

import dev.jacobandersen.bastion.micropub.type.GetQueryOption
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap

@Service
class GetDispatchService(
    val configService: ConfigService,
    val sourceService: SourceService,
    val syndicationTargetsService: SyndicationTargetsService,
) {
    fun handleMicropubGet(
        command: GetQueryOption,
        params: MutableMap<String, Array<String>>,
    ): ApiResponse<*> =
        when (command) {
            GetQueryOption.CONFIG -> configService.getConfig()
            GetQueryOption.SOURCE -> sourceService.getSource(params)
            GetQueryOption.SYNDICATE_TO -> syndicationTargetsService.getSyndicationTargets()
        }
}
