package io.github.mralex1810.fantasy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class FantasyApplication

fun main(args: Array<String>) {
    runApplication<FantasyApplication>(*args)
}
