package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.PostAction
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap
import org.springframework.web.multipart.MultipartFile

@Service
class PostDispatchService(
    val createService: CreateService,
    val updateService: UpdateService,
    val deleteService: DeleteService
) {
    fun handleMicropubPost(action: PostAction, payload: MicropubPayload, file: MultiValueMap<String, MultipartFile>?): ApiResponse<*> {
        return when (action) {
            PostAction.CREATE -> createService.create(payload, file)
            PostAction.UPDATE -> updateService.update(payload)
            PostAction.DELETE -> deleteService.delete(payload)
            PostAction.UNDELETE -> deleteService.undelete(payload)
        }
    }
}