package dev.jacobandersen.bastion.micropub.media

sealed interface FileUploadResult {
    data class Success(val name: String, val url: String) : FileUploadResult
    data class Failure(val error: Throwable) : FileUploadResult
}