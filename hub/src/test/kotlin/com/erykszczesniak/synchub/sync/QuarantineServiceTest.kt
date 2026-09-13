package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.repository.QuarantineReason
import com.erykszczesniak.synchub.repository.QuarantineStatus
import com.erykszczesniak.synchub.repository.QuarantinedRecordRepository
import com.erykszczesniak.synchub.source.SourceRecord
import com.fasterxml.jackson.databind.ObjectMapper
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
@Import(QuarantineService::class)
class QuarantineServiceTest {
    @Autowired
    private lateinit var service: QuarantineService

    @Autowired
    private lateinit var repository: QuarantinedRecordRepository

    private val json = ObjectMapper()
    private val t0 = Instant.parse("2026-03-01T10:00:00Z")
    private val runId = UUID.randomUUID()

    @Test
    fun `the raw payload is kept verbatim and a newer version supersedes the older quarantine`() {
        val v1 =
            service.quarantine(
                record(t0, """{"id":"cus_1","emailAddress":"a@b.c"}"""),
                runId,
                QuarantineReason.DRIFT,
                "renamed",
            )
        val v2 =
            service.quarantine(
                record(t0.plusSeconds(5), """{"id":"cus_1","emailAddress":"x@y.z"}"""),
                runId,
                QuarantineReason.DRIFT,
                "renamed",
            )

        assertThat(repository.findById(v1.id).orElseThrow().status).isEqualTo(QuarantineStatus.SUPERSEDED)
        val open = repository.findByFeedAndBusinessKeyAndStatus("customers", "cus_1", QuarantineStatus.OPEN)
        assertThat(open.single().id).isEqualTo(v2.id)
        assertThat(open.single().payload).isEqualTo("""{"id":"cus_1","emailAddress":"x@y.z"}""")
        assertThat(open.single().sourceUpdatedAt).isEqualTo(t0.plusSeconds(5))
    }

    @Test
    fun `a successful load at or after the quarantined version closes it, an older load does not`() {
        service.quarantine(record(t0.plusSeconds(10), "{}"), runId, QuarantineReason.VALIDATION, "bad email")

        assertThat(service.supersede("customers", "cus_1", loadedVersion = t0)).isZero()
        assertThat(repository.countByFeedAndStatus("customers", QuarantineStatus.OPEN)).isEqualTo(1)

        assertThat(service.supersede("customers", "cus_1", loadedVersion = t0.plusSeconds(10))).isEqualTo(1)
        assertThat(repository.countByFeedAndStatus("customers", QuarantineStatus.OPEN)).isZero()
    }

    @Test
    fun `discard and replay change the status with a note`() {
        val a = service.quarantine(record(t0, "{}"), runId, QuarantineReason.MAPPING, "x")
        service.discard(a.id, "test data")
        assertThat(repository.findById(a.id).orElseThrow().status).isEqualTo(QuarantineStatus.DISCARDED)

        val b = service.quarantine(record(t0.plusSeconds(1), "{}"), runId, QuarantineReason.MAPPING, "x")
        service.markReplayed(b.id, "replayed in run 42")
        assertThat(repository.findById(b.id).orElseThrow().resolutionNote).isEqualTo("replayed in run 42")
    }

    private fun record(
        updatedAt: Instant,
        payload: String,
    ) = SourceRecord("customers", "cus_1", updatedAt, false, json.readTree(payload))
}
