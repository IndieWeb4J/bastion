package dev.jacobandersen.bastion.config

import dev.jacobandersen.mf24j.Mf2Parser
import dev.jacobandersen.mf24j.Mf2ParserImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Mf2ParserConfig {
    @Bean
    fun mf2Parser(): Mf2Parser = Mf2ParserImpl()
}
