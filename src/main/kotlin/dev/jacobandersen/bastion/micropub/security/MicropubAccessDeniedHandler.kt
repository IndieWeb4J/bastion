package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.resp.toResponseEntity
import dev.jacobandersen.bastion.micropub.type.resp.writeResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class MicropubAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        // This exception occurs on authorization-check failures, i.e. scope failures
        // Per Micropub spec, we return Insufficient Scope
        ApiResponse.Error
            .InsufficientScope()
            .toResponseEntity()
            .writeResponse(response, objectMapper)
    }
}
