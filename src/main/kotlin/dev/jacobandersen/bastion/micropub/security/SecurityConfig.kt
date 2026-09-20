package dev.jacobandersen.bastion.micropub.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig(
    val accessDeniedHandler: MicropubAccessDeniedHandler,
    validator: MicropubTokenValidator,
    objectMapper: ObjectMapper,
) {
    private val authFilter = MicropubAuthenticationFilter(validator, objectMapper)

    @Bean
    @Order(1)
    fun micropubSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/micropub", "/micropub/**")
            .cors { }
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.anyRequest().authenticated()
            }.exceptionHandling { it.accessDeniedHandler(accessDeniedHandler) }
            .build()
}
