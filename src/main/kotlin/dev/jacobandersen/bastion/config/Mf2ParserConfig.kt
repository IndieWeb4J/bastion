package dev.jacobandersen.bastion.config

import dev.jacobandersen.bastion.microformats2.Mf2Parser
import dev.jacobandersen.bastion.microformats2.Mf2ParserImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Mf2ParserConfig {
    @Bean
    fun mf2Parser(): Mf2Parser = Mf2ParserImpl()
}
