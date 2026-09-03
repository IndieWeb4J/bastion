package dev.jacobandersen.bastion.micropub.service.get

import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.resp.SyndicateToResponse
import org.springframework.stereotype.Service

@Service
class SyndicationTargetsService {
    fun getSyndicationTargets(): ApiResponse<*> {
        return ApiResponse.Success.Ok(SyndicateToResponse(listOf()))
    }
}