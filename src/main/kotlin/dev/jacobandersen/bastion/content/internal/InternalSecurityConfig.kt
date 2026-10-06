package dev.jacobandersen.bastion.content.internal

import dev.jacobandersen.bastion.config.applyBastionDefaults
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableConfigurationProperties(ContentServiceTokenProperties::class)
class InternalSecurityConfig {
    @Bean
    @Order(0)
    fun internalSecurityFilterChain(
        http: HttpSecurity,
        properties: ContentServiceTokenProperties,
    ): SecurityFilterChain =
        http
            .securityMatcher("/internal/**")
            .applyBastionDefaults()
            .addFilterBefore(ContentServiceTokenFilter(properties), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}