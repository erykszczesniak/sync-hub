package com.erykszczesniak.systema

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SystemASimApplication

fun main(args: Array<String>) {
    runApplication<SystemASimApplication>(*args)
}
