package dev.jacobandersen.bastion.indieauth.security

import dev.jacobandersen.bastion.config.applyBastionDefaults
import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource

@Configuration
class IndieAuthSecurityConfig {
    @Bean
    @Order(1)
    fun indieAuthSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher(
                IndieAuthEndpoints.TOKEN,
                "/.well-known/oauth-authorization-server",
                "/.well-known/oauth-authorization-endpoint",
                "/.well-known/oauth-token-endpoint",
            ).applyBastionDefaults()
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOriginPatterns = listOf("*")
                allowedMethods = listOf("GET", "POST", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = false
                maxAge = 3600L
            }
        return CorsConfigurationSource { _ -> config }
    }
}
