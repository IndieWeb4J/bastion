package dev.jacobandersen.bastion.security

import org.springframework.beans.factory.annotation.Configurable
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.web.multipart.support.MultipartFilter

@Configurable
class SecurityConfig {
    @Bean
    fun multipartFilterRegistrationBean(): FilterRegistrationBean<MultipartFilter> {
        val bean = FilterRegistrationBean<MultipartFilter>()
        bean.setFilter(MultipartFilter())
        bean.order = Ordered.HIGHEST_PRECEDENCE
        return bean
    }
}
