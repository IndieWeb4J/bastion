package dev.jacobandersen.bastion.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class GlobalSecurityConfig(
    private val corsProperties: CorsProperties,
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                val origins = corsProperties.allowedOrigins.filter { it.isNotBlank() }
                if (origins.isNotEmpty()) {
                    allowedOrigins = origins
                }
                allowedMethods = corsProperties.allowedMethods.filter { it.isNotBlank() }
                allowedHeaders = corsProperties.allowedHeaders.filter { it.isNotBlank() }
                if (corsProperties.exposedHeaders.any { it.isNotBlank() }) {
                    exposedHeaders = corsProperties.exposedHeaders.filter { it.isNotBlank() }
                }
                allowCredentials = corsProperties.allowCredentials
                maxAge = corsProperties.maxAge
            }

        return UrlBasedCorsConfigurationSource().apply {
            if (corsProperties.allowedOrigins.any { it.isNotBlank() }) {
                registerCorsConfiguration("/**", config)
            }
        }
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .applyBastionDefaults()
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}
