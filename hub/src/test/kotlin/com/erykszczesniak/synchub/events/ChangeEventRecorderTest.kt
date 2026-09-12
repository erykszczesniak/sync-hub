package com.erykszczesniak.synchub.events

import com.erykszczesniak.synchub.canonical.ChangeType
import com.erykszczesniak.synchub.repository.OutboxEventRepository
import com.erykszczesniak.synchub.sink.LoadOutcome
import com.erykszczesniak.synchub.sink.LoadResult
import com.erykszczesniak.synchub.sink.SystemBOrderRecord
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ChangeEventRecorder::class, JacksonAutoConfiguration::class)
class ChangeEventRecorderTest {
    @Autowired
    private lateinit var recorder: ChangeEventRecorder

    @Autowired
    private lateinit var outbox: OutboxEventRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val t0 = Instant.parse("2026-03-01T10:00:00Z")
    private val runId = UUID.randomUUID()

    @org.junit.jupiter.api.BeforeEach
    fun clean() {
        outbox.deleteAll()
    }

    @Test
    fun `an applied load becomes an outbox row with the full event payload`() {
        val result = LoadResult("ord_1", LoadOutcome.UPDATED, row("PEN"), row("PAI"), 2, t0)

        val event = recorder.record("orders", runId, result, now = t0.plusSeconds(1))!!

        val stored = outbox.findById(event.eventId).orElseThrow()
        assertThat(stored.feed).isEqualTo("orders")
        assertThat(stored.businessKey).isEqualTo("ord_1")
        assertThat(stored.eventType).isEqualTo(ChangeType.UPDATED)
        assertThat(stored.publishedAt).isNull()
        val payload = objectMapper.readTree(stored.payload)
        assertThat(payload["eventId"].asText()).isEqualTo(event.eventId.toString())
        assertThat(payload["type"].asText()).isEqualTo("UPDATED")
        assertThat(payload["version"].asInt()).isEqualTo(2)
        assertThat(payload["runId"].asText()).isEqualTo(runId.toString())
        assertThat(payload["before"]["stateCode"].asText()).isEqualTo("PEN")
        assertThat(payload["after"]["stateCode"].asText()).isEqualTo("PAI")
        assertThat(payload["sourceUpdatedAt"].asText()).isEqualTo("2026-03-01T10:00:00Z")
    }

    @Test
    fun `skipped loads produce no event because nothing changed`() {
        assertThat(
            recorder.record(
                "orders",
                runId,
                LoadResult("ord_1", LoadOutcome.SKIPPED_UNCHANGED, row("PEN"), null, 1, t0),
            ),
        ).isNull()
        assertThat(
            recorder.record("orders", runId, LoadResult("ord_1", LoadOutcome.SKIPPED_STALE, row("PEN"), null, 1, t0)),
        ).isNull()

        assertThat(outbox.count()).isZero()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `recording outside the load transaction is refused`() {
        val result = LoadResult("ord_1", LoadOutcome.CREATED, null, row("PEN"), 1, t0)

        assertThrows<IllegalTransactionStateException> { recorder.record("orders", runId, result) }
    }

    private fun row(state: String) =
        SystemBOrderRecord("ord_1", "cus_1", state, 1000, "EUR", 1, "[]", t0.minusSeconds(60), t0)
}
