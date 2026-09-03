package dev.jacobandersen.bastion.micropub.media

import com.github.slugify.Slugify
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaTypeFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class FileUploadService(
    val slugify: Slugify,
    val s3Client: S3Client,
    val mediaConfiguration: BastionMediaConfiguration
) {
    fun upload(file: MultipartFile): FileUploadResult {
        val fileId = UUID.randomUUID().toString()

        val originalName = file.originalFilename
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }

        val fileExt = originalName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?: "blob"

        val stem = originalName
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: file.name

        val safeStem = slugify.slugify(stem)
        val fileName = "$safeStem-$fileId.$fileExt"

        val contentType = file.contentType?.takeIf { it.isNotBlank() }
            ?: originalName?.let { MediaTypeFactory.getMediaType(it).orElse(null)?.toString() }

        return try {
            logger.info { "Uploading ${fileName}..." }

            val requestBuilder = PutObjectRequest.builder()
                .bucket(mediaConfiguration.s3.bucket)
                .key(fileName)

            if (contentType != null) {
                requestBuilder.contentType(contentType)
            }

            s3Client.putObject(
                requestBuilder.build(),
                RequestBody.fromBytes(file.bytes)
            )

            FileUploadResult.Success(file.name, "${mediaConfiguration.baseUrl.trimEnd('/')}/${fileName}")
        } catch (e: Exception) {
            FileUploadResult.Failure(e)
        }
    }
}
