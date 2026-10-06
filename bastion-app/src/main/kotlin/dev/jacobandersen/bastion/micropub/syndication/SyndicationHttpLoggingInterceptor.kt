package dev.jacobandersen.bastion.micropub.syndication

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

private val logger = KotlinLogging.logger {}

class SyndicationHttpLoggingInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        logger.info { "--> Syndication: ${request.method} ${request.uri}" }
        val start = System.currentTimeMillis()
        val resp = execution.execute(request, body)
        logger.info { "<-- Syndication (in ${System.currentTimeMillis() - start} ms): ${resp.statusCode}" }
        return resp
    }
}
