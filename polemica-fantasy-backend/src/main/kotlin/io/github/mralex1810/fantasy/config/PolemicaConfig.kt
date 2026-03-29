package io.github.mralex1810.fantasy.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mafia.vyasma.polemica.library.client.GamePointsService
import com.github.mafia.vyasma.polemica.library.client.PolemicaClient
import com.github.mafia.vyasma.polemica.library.client.PolemicaClientImpl
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.web.client.RestTemplate

@Configuration
@EnableConfigurationProperties(PolemicaProperties::class)
class PolemicaConfig {

    @Bean
    fun polemicaJacksonCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder: Jackson2ObjectMapperBuilder ->
            builder.findModulesViaServiceLoader(true)
        }

    @Bean
    fun polemicaClient(properties: PolemicaProperties, builder: Jackson2ObjectMapperBuilder): PolemicaClient {
        val objectMapper = builder.build<ObjectMapper>()
        return PolemicaClientImpl(
            properties.baseUrl.removeSuffix("/"),
            properties.username,
            properties.password,
            objectMapper,
            properties.profileSiteBaseUrl.removeSuffix("/"),
        )
    }

    @Bean
    fun polemicaPublicRestTemplate(builder: RestTemplateBuilder): RestTemplate = builder.build()

    @Bean
    fun gamePointsService(polemicaPublicRestTemplate: RestTemplate): GamePointsService =
        GamePointsService(polemicaPublicRestTemplate)
}
