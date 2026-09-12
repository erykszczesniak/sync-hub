package com.erykszczesniak.synchub.source

import io.netty.channel.ChannelOption
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
class SystemAClientConfig {
    /** One WebClient for System A: base URL, API key and timeouts set once, shared by REST and GraphQL clients. */
    @Bean
    fun systemAWebClient(
        properties: SystemAProperties,
        builder: WebClient.Builder,
    ): WebClient {
        val http =
            HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeout.toMillis().toInt())
                .responseTimeout(properties.readTimeout)
        return builder
            .baseUrl(properties.baseUrl)
            .defaultHeader(API_KEY_HEADER, properties.apiKey)
            .clientConnector(ReactorClientHttpConnector(http))
            .build()
    }

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
        const val RESILIENCE_INSTANCE = "system-a"
    }
}
