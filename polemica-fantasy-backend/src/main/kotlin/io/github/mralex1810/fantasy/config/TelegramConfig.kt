package io.github.mralex1810.fantasy.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class TelegramConfig {

    @Bean
    fun telegramRestClient(): RestClient {
        val factory = JdkClientHttpRequestFactory()
        factory.setReadTimeout(Duration.ofSeconds(30))
        return RestClient.builder()
            .requestFactory(factory)
            .baseUrl("https://api.telegram.org")
            .build()
    }
}
