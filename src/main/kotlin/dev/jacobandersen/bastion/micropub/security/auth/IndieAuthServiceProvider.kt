package dev.jacobandersen.bastion.micropub.security.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient

@Component
class IndieAuthServiceProvider(
    @Value($$"${bastion.indieauth.validate-token-url}")
    private val indieAuthUrl: String,
) {
    @Bean
    fun indieAuthService(): IndieAuthService {
        val client =
            RestClient
                .builder()
                .baseUrl(indieAuthUrl)
                .requestInterceptor(IndieAuthServiceLoggingInterceptor())
                .build()

        val adapter = RestClientAdapter.create(client)

        val factory =
            HttpServiceProxyFactory
                .builderFor(adapter)
                .build()

        return factory.createClient<IndieAuthService>()
    }
}
