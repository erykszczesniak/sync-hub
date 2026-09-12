package com.erykszczesniak.synchub.events

import com.erykszczesniak.synchub.repository.OutboxEventRepository
import com.erykszczesniak.synchub.sink.LoadOutcome
import com.erykszczesniak.synchub.sink.LoadResult
import com.erykszczesniak.synchub.sink.SystemBCustomerRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** End to end through the outbox to a real broker: what a downstream consumer would actually receive. */
@SpringBootTest(properties = ["synchub.events.transport=kafka", "synchub.events.partitions=1"])
@Testcontainers
@ActiveProfiles("test")
class KafkaChangeEventsTest {
    @Autowired
    private lateinit var recorder: ChangeEventRecorder

    @Autowired
    private lateinit var publisher: OutboxPublisher

    @Autowired
    private lateinit var outbox: OutboxEventRepository

    @Autowired
    private lateinit var transactions: TransactionTemplate

    @Autowired
    private lateinit var transport: ChangeEventTransport

    @Test
    fun `applied changes reach the feed topic keyed by business key, in order, and are marked published`() {
        assertThat(transport.name).isEqualTo("kafka")
        val t0 = Instant.parse("2026-03-01T10:00:00Z")
        val runId = UUID.randomUUID()
        transactions.execute {
            recorder.record("customers", runId, LoadResult("cus_7", LoadOutcome.CREATED, null, row(t0, 1), 1, t0))
            recorder.record(
                "customers",
                runId,
                LoadResult("cus_7", LoadOutcome.UPDATED, row(t0, 1), row(t0.plusSeconds(1), 2), 2, t0.plusSeconds(1)),
            )
            recorder.record(
                "customers",
                runId,
                LoadResult("cus_7", LoadOutcome.SKIPPED_UNCHANGED, row(t0, 2), null, 2, t0.plusSeconds(2)),
            )
        }
        assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(2)

        val summary = publisher.publishPending()

        assertThat(summary).isEqualTo(PublishSummary(published = 2, failed = 0))
        assertThat(outbox.countByPublishedAtIsNull()).isZero()

        consumer("synchub.changes.customers").use { consumer ->
            val records = consumer.poll(Duration.ofSeconds(15)).records("synchub.changes.customers").toList()
            assertThat(records).hasSize(2)
            assertThat(records.map { it.key() }).containsOnly("cus_7")
            assertThat(records[0].value()).contains("\"type\":\"CREATED\"").contains("\"version\":1")
            assertThat(
                records[1].value(),
            ).contains("\"type\":\"UPDATED\"").contains("\"version\":2").contains("\"tierRank\":2")
        }
    }

    private fun consumer(topic: String): KafkaConsumer<String, String> {
        val props =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            )
        return KafkaConsumer<String, String>(props).also { it.subscribe(listOf(topic)) }
    }

    private fun row(
        updatedAt: Instant,
        tier: Int,
    ) = SystemBCustomerRecord(
        "cus_7",
        "a@b.c",
        "A B",
        "A",
        "B",
        "A",
        tier,
        "1 St",
        "Warsaw",
        "00-001",
        "PL",
        "",
        updatedAt,
        updatedAt,
    )

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val kafka: KafkaContainer = KafkaContainer("apache/kafka:3.9.1")
    }
}
