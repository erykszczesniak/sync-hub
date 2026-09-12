package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.repository.DriftEventRepository
import com.erykszczesniak.synchub.repository.DriftStatus
import com.erykszczesniak.synchub.transform.DriftFinding
import com.erykszczesniak.synchub.transform.DriftKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(DriftEventService::class)
class DriftEventServiceTest {
    @Autowired
    private lateinit var service: DriftEventService

    @Autowired
    private lateinit var repository: DriftEventRepository

    private val rename = DriftFinding(DriftKind.FIELD_RENAMED, "email", "email", "emailAddress", "renamed")
    private val t0 = Instant.parse("2026-03-01T10:00:00Z")

    @Test
    fun `repeated detections collapse into one open event with the earliest source change`() {
        val run1 = UUID.randomUUID()
        val run2 = UUID.randomUUID()

        service.record(
            "customers",
            run1,
            listOf(rename),
            sourceUpdatedAt = t0.plusSeconds(30),
            now = t0.plusSeconds(60),
        )
        service.record("customers", run1, listOf(rename), sourceUpdatedAt = t0, now = t0.plusSeconds(60))
        service.record(
            "customers",
            run2,
            listOf(rename),
            sourceUpdatedAt = t0.plusSeconds(90),
            now = t0.plusSeconds(120),
        )

        val open = repository.findByFeedAndStatus("customers", DriftStatus.OPEN)
        assertThat(open).hasSize(1)
        with(open.single()) {
            assertThat(affectedRecords).isEqualTo(3)
            assertThat(firstRunId).isEqualTo(run1)
            assertThat(lastRunId).isEqualTo(run2)
            assertThat(sourceChangedAt).isEqualTo(t0)
            assertThat(detectedAt).isEqualTo(t0.plusSeconds(60))
            assertThat(lastSeenAt).isEqualTo(t0.plusSeconds(120))
        }
    }

    @Test
    fun `resolving closes the event and a new detection opens a fresh one`() {
        val runId = UUID.randomUUID()
        val event = service.record("customers", runId, listOf(rename), t0, now = t0).single()

        service.resolve(event.id, "contract updated", now = t0.plusSeconds(600))
        val resolved = repository.findById(event.id).orElseThrow()
        assertThat(resolved.status).isEqualTo(DriftStatus.RESOLVED)
        assertThat(resolved.resolvedAt).isEqualTo(t0.plusSeconds(600))
        assertThat(resolved.resolutionNote).isEqualTo("contract updated")

        val again = service.record("customers", runId, listOf(rename), t0, now = t0.plusSeconds(700)).single()
        assertThat(again.id).isNotEqualTo(event.id)
        assertThat(repository.countByFeedAndStatus("customers", DriftStatus.OPEN)).isEqualTo(1)
    }

    @Test
    fun `resolveAllOpen only touches the given feed`() {
        val runId = UUID.randomUUID()
        service.record("customers", runId, listOf(rename), t0)
        service.record(
            "orders",
            runId,
            listOf(DriftFinding(DriftKind.VALUE_OUT_OF_DOMAIN, "status", "PAID", "ON_HOLD", "x")),
            t0,
        )

        val resolved = service.resolveAllOpen("customers", "clean backfill")

        assertThat(resolved).isEqualTo(1)
        assertThat(repository.countByFeedAndStatus("customers", DriftStatus.OPEN)).isZero()
        assertThat(repository.countByFeedAndStatus("orders", DriftStatus.OPEN)).isEqualTo(1)
    }
}
