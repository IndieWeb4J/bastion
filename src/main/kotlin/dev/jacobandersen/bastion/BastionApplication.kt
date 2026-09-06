package dev.jacobandersen.bastion

import dev.jacobandersen.bastion.config.BastionNativeHints
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@SpringBootApplication
@EnableWebSecurity
@EnableMethodSecurity
@ConfigurationPropertiesScan
@ImportRuntimeHints(BastionNativeHints::class)
class BastionApplication

fun main(args: Array<String>) {
    runApplication<BastionApplication>(*args)
}
