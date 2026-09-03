package dev.jacobandersen.bastion.micropub.controller

import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.PostAction
import dev.jacobandersen.bastion.micropub.type.resp.toResponseEntity
import dev.jacobandersen.bastion.micropub.service.post.PostDispatchService
import dev.jacobandersen.bastion.micropub.util.MicropubParamNormalizer
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import tools.jackson.databind.node.ObjectNode

@RestController
@RequestMapping("/micropub")
class PostController(val service: PostDispatchService) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun onJson(@RequestBody data: ObjectNode): ResponseEntity<*> {
        val actionNode = data["action"]
        val action = if (actionNode == null || actionNode.isNull) {
            PostAction.CREATE
        } else {
            if (!actionNode.isString) {
                return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid action parameter").toResponseEntity()
            }
            PostAction.fromString(actionNode.asString())
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Unknown action").toResponseEntity()
        }
        data.remove("action")

        return service.handleMicropubPost(action, MicropubPayload.Json(data), null).toResponseEntity()
    }

    @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun onUrlEncoded(@RequestBody data: MultiValueMap<String, String>): ResponseEntity<*> {
        return onFormDataLike(data.entries.associate { it.key to it.value.toTypedArray() }, null)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun onMultipart(request: MultipartHttpServletRequest): ResponseEntity<*> {
        return onFormDataLike(request.parameterMap, request.multiFileMap)
    }

    private fun onFormDataLike(data: Map<String, Array<String>>, files: MultiValueMap<String, MultipartFile>?): ResponseEntity<*> {
        val normalizedData = MicropubParamNormalizer.normalizeDuplicates(data)

        val rawAction = normalizedData["action"]?.firstOrNull()
        val action = if (rawAction.isNullOrBlank()) {
            PostAction.CREATE
        } else {
            PostAction.fromString(rawAction)
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Unknown action").toResponseEntity()
        }
        normalizedData.remove("action")

        return service.handleMicropubPost(action, MicropubPayload.Form(normalizedData), files).toResponseEntity()
    }
}