package dev.jacobandersen.bastion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@SpringBootApplication
@EnableWebSecurity
@EnableMethodSecurity
@ConfigurationPropertiesScan
class BastionApplication

fun main(args: Array<String>) {
    runApplication<BastionApplication>(*args)
}
