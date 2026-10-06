package dev.jacobandersen.bastion.micropub.media

import com.github.slugify.Slugify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

class FileUploadServiceTest {
    private val slugify =
        Slugify
            .builder()
            .lowerCase(true)
            .transliterator(true)
            .underscoreSeparator(false)
            .build()

    private val s3Client = mock(S3Client::class.java)
    private val config =
        BastionMediaConfiguration(
            baseUrl = "https://media.bastion.test/",
            s3 =
                BastionMediaConfiguration.S3Configuration(
                    accessKeyId = "access",
                    secretAccessKey = "secret",
                    endpoint = "https://s3.bastion.test",
                    region = "us-east-1",
                    bucket = "bastion-test",
                ),
        )

    private val service = FileUploadService(slugify, s3Client, config)

    @Test
    fun `uploads a file with a slugified unique name and returns its url`() {
        `when`(
            s3Client.putObject(
                org.mockito.ArgumentMatchers.any(PutObjectRequest::class.java),
                org.mockito.ArgumentMatchers.any(RequestBody::class.java),
            ),
        ).thenReturn(PutObjectResponse.builder().build())
        val file = MockMultipartFile("photo", "Sunset Photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))

        val result = service.upload(file)

        assertTrue(result is FileUploadResult.Success)
        val success = result as FileUploadResult.Success
        assertEquals("photo", success.name)
        assertTrue(success.url.startsWith("https://media.bastion.test/sunset-photo-"), success.url)
        assertTrue(success.url.endsWith(".jpg"), success.url)
    }

    @Test
    fun `falls back to the part name and blob extension without an original filename`() {
        `when`(
            s3Client.putObject(
                org.mockito.ArgumentMatchers.any(PutObjectRequest::class.java),
                org.mockito.ArgumentMatchers.any(RequestBody::class.java),
            ),
        ).thenReturn(PutObjectResponse.builder().build())
        val file = MockMultipartFile("photo", null, "image/jpeg", byteArrayOf(1, 2, 3))

        val result = service.upload(file)

        val success = result as FileUploadResult.Success
        assertTrue(success.url.startsWith("https://media.bastion.test/photo-"), success.url)
        assertTrue(success.url.endsWith(".blob"), success.url)
    }

    @Test
    fun `reports failure when the s3 upload throws`() {
        `when`(
            s3Client.putObject(
                org.mockito.ArgumentMatchers.any(PutObjectRequest::class.java),
                org.mockito.ArgumentMatchers.any(RequestBody::class.java),
            ),
        ).thenThrow(RuntimeException("s3 down"))
        val file = MockMultipartFile("photo", "a.jpg", "image/jpeg", byteArrayOf(1, 2, 3))

        val result = service.upload(file)

        assertTrue(result is FileUploadResult.Failure)
        assertEquals("s3 down", (result as FileUploadResult.Failure).error.message)
    }

    @Test
    fun `applies the content type when the file does not declare one`() {
        var captured: PutObjectRequest? = null
        `when`(
            s3Client.putObject(
                org.mockito.ArgumentMatchers.any(PutObjectRequest::class.java),
                org.mockito.ArgumentMatchers.any(RequestBody::class.java),
            ),
        ).thenAnswer {
            captured = it.getArgument(0)
            PutObjectResponse.builder().build()
        }
        val file = MockMultipartFile("photo", "image.png", null, byteArrayOf(1, 2, 3))

        service.upload(file)

        assertEquals("image/png", captured!!.contentType())
    }
}
