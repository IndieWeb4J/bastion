package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.config.applyBastionDefaults
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig(
    val accessDeniedHandler: MicropubAccessDeniedHandler,
    validator: MicropubTokenValidator,
    objectMapper: ObjectMapper,
    @Qualifier("publicCorsConfigurationSource") private val publicCorsConfigurationSource: CorsConfigurationSource,
) {
    private val authFilter = MicropubAuthenticationFilter(validator, objectMapper)

    @Bean
    @Order(1)
    fun micropubSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/micropub", "/micropub/**")
            .applyBastionDefaults()
            .cors { it.configurationSource(publicCorsConfigurationSource) }
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling { it.accessDeniedHandler(accessDeniedHandler) }
            .build()
}
