package dev.jacobandersen.bastion.micropub.service.media

import dev.jacobandersen.bastion.micropub.media.FileUploadResult
import dev.jacobandersen.bastion.micropub.media.FileUploadService
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger { }

@Service
class MediaService(
    val fileUploadService: FileUploadService
) {
    @PreAuthorize("hasAuthority('MEDIA')")
    fun handle(file: MultipartFile): ApiResponse<*> {
        return when (val result = fileUploadService.upload(file)) {
            is FileUploadResult.Success -> {
                logger.info { "Media endpoint file upload success" }
                ApiResponse.Success.Created(result.url)
            }

            is FileUploadResult.Failure -> {
                logger.info { "Media endpoint file upload failure: ${result.error.message}" }
                ApiResponse.Error.Unknown(errorDescription = "Media endpoint file upload failure")
            }
        }
    }
}