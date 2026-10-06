package dev.jacobandersen.bastion.content.media

sealed interface FileUploadResult {
    data class Success(
        val name: String,
        val url: String,
    ) : FileUploadResult

    data class Failure(
        val error: Throwable,
    ) : FileUploadResult
}
