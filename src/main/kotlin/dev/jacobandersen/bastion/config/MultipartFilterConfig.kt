package dev.jacobandersen.bastion.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.multipart.support.MultipartFilter

/**
 * Registers [MultipartFilter] ahead of the security filter chain so that
 * `multipart/form-data` requests are parsed before the Micropub authentication
 * filter inspects their parameters (e.g. `access_token` submitted in the form
 * body).
 */
@Configuration
class MultipartFilterConfig {
    @Bean
    fun multipartFilterRegistrationBean(): FilterRegistrationBean<MultipartFilter> {
        val bean = FilterRegistrationBean<MultipartFilter>()
        bean.setFilter(MultipartFilter())
        bean.order = Ordered.HIGHEST_PRECEDENCE
        return bean
    }
}
