package com.erykszczesniak.synchub.events

import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** Where published events go. Must not return before the broker acknowledged the record. */
interface ChangeEventTransport {
    val name: String

    fun publish(
        topic: String,
        key: String,
        payload: String,
    )
}

@Component
@ConditionalOnProperty(prefix = "synchub.events", name = ["transport"], havingValue = "kafka")
class KafkaChangeEventTransport(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) : ChangeEventTransport {
    override val name: String = "kafka"

    /** Synchronous send: the outbox row is marked published only once Kafka has acknowledged (acks=all). */
    override fun publish(
        topic: String,
        key: String,
        payload: String,
    ) {
        kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        private const val SEND_TIMEOUT_SECONDS = 30L
    }
}

/** Development transport (H2 profile, no broker): events are written to the log at INFO. */
@Component
@ConditionalOnProperty(prefix = "synchub.events", name = ["transport"], havingValue = "log", matchIfMissing = true)
class LoggingChangeEventTransport : ChangeEventTransport {
    private val log = LoggerFactory.getLogger("synchub.change-events")

    override val name: String = "log"

    override fun publish(
        topic: String,
        key: String,
        payload: String,
    ) {
        log.info("[{}] key={} {}", topic, key, payload)
    }
}

@Configuration
@ConditionalOnProperty(prefix = "synchub.events", name = ["transport"], havingValue = "kafka")
class ChangeEventTopics(
    private val properties: EventsProperties,
) {
    @Bean
    fun customersChangesTopic(): NewTopic =
        NewTopic(properties.topicFor("customers"), properties.partitions, 1.toShort())

    @Bean
    fun ordersChangesTopic(): NewTopic = NewTopic(properties.topicFor("orders"), properties.partitions, 1.toShort())
}
