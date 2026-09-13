package com.erykszczesniak.synchub.source

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "synchub.source.system-a")
data class SystemAProperties(
    val baseUrl: String,
    val apiKey: String,
    val pageSize: Int = 100,
    val maxPagesPerRun: Int = 100,
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(10),
)
