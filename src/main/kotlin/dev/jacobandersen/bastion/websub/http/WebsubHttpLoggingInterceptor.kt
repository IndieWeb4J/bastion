package dev.jacobandersen.bastion.websub.http

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

private val logger = KotlinLogging.logger {}

class WebsubHttpLoggingInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        logRequest(request)
        val start = System.currentTimeMillis()
        val resp = execution.execute(request, body)
        logResponse(resp, System.currentTimeMillis() - start)
        return resp
    }

    private fun logRequest(request: HttpRequest) {
        logger.info { "--> WebSub: ${request.method} ${request.uri}" }
    }

    private fun logResponse(
        response: ClientHttpResponse,
        elapsedNanos: Long,
    ) {
        logger.info { "<-- WebSub (in $elapsedNanos ms): ${response.statusCode}" }
    }
}
