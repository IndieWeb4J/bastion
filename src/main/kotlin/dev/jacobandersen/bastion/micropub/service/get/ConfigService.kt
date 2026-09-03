package dev.jacobandersen.bastion.micropub.service.get

import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.resp.ConfigResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ConfigService(
    @Value($$"${bastion.public-url}") val publicUrl: String,
) {
    fun getConfig(): ApiResponse<*> {
        val mediaEndpoint = "$publicUrl/micropub/media"

        return ApiResponse.Success.Ok(ConfigResponse(mediaEndpoint, listOf()))
    }
}