package dev.jacobandersen.bastion.micropub.controller

import dev.jacobandersen.bastion.micropub.service.get.GetDispatchService
import dev.jacobandersen.bastion.micropub.type.GetQueryOption
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.resp.toResponseEntity
import dev.jacobandersen.bastion.micropub.util.MicropubParamNormalizer
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/micropub")
class GetController(
    val service: GetDispatchService,
) {
    @GetMapping
    fun handle(
        @RequestParam params: MultiValueMap<String, String>,
    ): ResponseEntity<*> {
        val params = MicropubParamNormalizer.normalizeDuplicates(params)

        val command =
            GetQueryOption.fromString(params["q"]?.firstOrNull())
                ?: return ApiResponse.Error
                    .InvalidRequest(
                        errorDescription = "Unknown query command",
                    ).toResponseEntity()

        params.remove("q")

        return service.handleMicropubGet(command, params).toResponseEntity()
    }
}
