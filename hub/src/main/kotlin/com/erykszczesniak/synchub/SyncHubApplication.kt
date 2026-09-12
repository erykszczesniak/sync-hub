package com.erykszczesniak.synchub

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SyncHubApplication

fun main(args: Array<String>) {
    runApplication<SyncHubApplication>(*args)
}
