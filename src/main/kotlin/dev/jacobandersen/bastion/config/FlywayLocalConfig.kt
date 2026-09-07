package dev.jacobandersen.bastion.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

private val logger = KotlinLogging.logger {}

@Configuration
@Profile("local")
class FlywayLocalConfig {
    @Bean
    fun flywayMigrationStrategy(): FlywayMigrationStrategy =
        {
            logger.info { "Using LOCAL migration strategy" }

            it.clean()
            it.migrate()
        }
}
