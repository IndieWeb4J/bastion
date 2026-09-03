package dev.jacobandersen.bastion.micropub.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class FileUploadService(val s3Client: S3Client, val mediaConfiguration: BastionMediaConfiguration) {
    fun upload(file: MultipartFile): FileUploadResult {
        val fileId = UUID.randomUUID().toString()

        val fileExt = if (file.originalFilename != null) {
            File(file.originalFilename!!).extension
        } else {
            "blob"
        }

        val fileName = "${file.name}-$fileId.${fileExt}"

        return try {
            logger.info { "Uploading ${fileName}..." }
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(mediaConfiguration.s3.bucket)
                    .key(fileName)
                    .build(),
                RequestBody.fromBytes(file.bytes)
            )

            FileUploadResult.Success(file.name, "${mediaConfiguration.baseUrl}/${fileName}")
        } catch (e: Exception) {
            FileUploadResult.Failure(e)
        }
    }
}