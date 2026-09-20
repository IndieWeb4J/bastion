package dev.jacobandersen.bastion.webmention.controller

import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import dev.jacobandersen.bastion.webmention.http.SourceHostValidator
import dev.jacobandersen.bastion.webmention.service.WebmentionReceiverService
import dev.jacobandersen.bastion.webmention.service.WebmentionSubmissionLimiter
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.jobrunr.scheduling.JobScheduler
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartHttpServletRequest
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * The Webmention receiver endpoint (section 3.2 of the Webmention
 * specification). Performs synchronous request verification and returns 202,
 * deferring source verification to an asynchronous job.
 */
@RestController
@RequestMapping("/webmention")
class WebmentionController(
    private val notificationService: ReceivedWebmentionService,
    private val receiverService: WebmentionReceiverService,
    private val postService: PostService,
    private val urlService: UrlService,
    private val jobScheduler: JobScheduler,
    private val hostValidator: SourceHostValidator,
    private val submissionLimiter: WebmentionSubmissionLimiter,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun onUrlEncoded(request: HttpServletRequest): ResponseEntity<*> = handle(request.parameterMap)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun onMultipart(request: MultipartHttpServletRequest): ResponseEntity<*> = handle(request.parameterMap)

    private fun handle(params: Map<String, Array<String>>): ResponseEntity<*> {
        val sourceUrl = firstParam(params, "source")
        val targetUrl = firstParam(params, "target")

        if (sourceUrl == null) {
            return invalidRequest("The source parameter is required")
        }
        if (targetUrl == null) {
            return invalidRequest("The target parameter is required")
        }
        if (!isHttpUrl(sourceUrl)) {
            return invalidRequest("The source URL is not a valid http(s) URL")
        }
        if (!isHttpUrl(targetUrl)) {
            return invalidRequest("The target URL is not a valid http(s) URL")
        }
        if (sourceUrl == targetUrl) {
            return invalidRequest("The source and target URLs must be different")
        }
        if (hostValidator.isBlocked(sourceUrl)) {
            return invalidRequest("The source URL host is not reachable")
        }
        if (!submissionLimiter.allow(sourceUrl, targetUrl)) {
            return invalidRequest("Too many recent webmentions from this source")
        }

        val slug = urlService.extractPostSlug(targetUrl)
        val post = slug?.let { postService.findBySlug(it) }
        if (post == null || !post.publiclyReachable) {
            return invalidRequest("The target URL does not accept webmentions")
        }

        notificationService.ensurePending(sourceUrl, targetUrl, post.id)
        jobScheduler.enqueue {
            receiverService.verify(sourceUrl, targetUrl, post.id)
        }

        logger.info { "Accepted webmention from $sourceUrl for $targetUrl" }
        return ResponseEntity.accepted().build<Void>()
    }

    private fun firstParam(
        params: Map<String, Array<String>>,
        name: String,
    ): String? =
        params[name]?.firstOrNull()?.trim()?.takeIf {
            it.isNotEmpty()
        }

    private fun isHttpUrl(value: String): Boolean =
        runCatching {
            val uri = URI(value)
            uri.isAbsolute &&
                (uri.scheme == "http" || uri.scheme == "https") &&
                uri.host != null
        }.getOrDefault(false)

    private fun invalidRequest(description: String): ResponseEntity<*> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "error" to "invalid_request",
                    "error_description" to description,
                ),
            )
}
