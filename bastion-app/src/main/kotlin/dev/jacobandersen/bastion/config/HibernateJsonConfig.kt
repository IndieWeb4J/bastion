package dev.jacobandersen.bastion.config

import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class HibernateJsonConfig {
    @Bean
    fun hibernateJsonFormatMapper(): Jackson3JsonFormatMapper {
        val jsonMapper =
            JsonMapper
                .builder()
                .addModule(KotlinModule.Builder().build())
                .build()
        return Jackson3JsonFormatMapper(jsonMapper)
    }

    @Bean
    fun hibernateJsonFormatMapperCustomizer(mapper: Jackson3JsonFormatMapper): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { properties ->
            properties[HIBERNATE_JSON_FORMAT_MAPPER] = mapper
        }

    companion object {
        const val HIBERNATE_JSON_FORMAT_MAPPER = "hibernate.type.json_format_mapper"
    }
}
