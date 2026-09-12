package com.erykszczesniak.synchub.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/** Runs the Flyway baseline on H2 (PostgreSQL mode) and exercises the hub-state repositories. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class HubStateRepositoriesTest {
    @Autowired
    private lateinit var watermarks: FeedWatermarkRepository

    @Autowired
    private lateinit var runs: SyncRunRepository

    @Autowired
    private lateinit var quarantine: QuarantinedRecordRepository

    @Autowired
    private lateinit var drift: DriftEventRepository

    @Test
    fun `watermark is stored per feed and can be moved`() {
        val ts = Instant.parse("2026-01-01T00:00:00Z")
        watermarks.save(FeedWatermarkEntity(feed = "customers", watermarkTs = ts, watermarkKey = "cus_1"))

        val stored = watermarks.findById("customers").orElseThrow()
        stored.watermarkTs = ts.plusSeconds(60)
        watermarks.save(stored)

        assertThat(watermarks.findById("customers").orElseThrow().watermarkTs).isEqualTo(ts.plusSeconds(60))
    }

    @Test
    fun `runs are listed newest first per feed`() {
        val older = runs.save(run("customers", Instant.parse("2026-01-01T10:00:00Z")))
        val newer = runs.save(run("customers", Instant.parse("2026-01-01T11:00:00Z")))
        runs.save(run("orders", Instant.parse("2026-01-01T12:00:00Z")))

        val page = runs.findByFeedOrderByStartedAtDesc("customers", PageRequest.of(0, 10))

        assertThat(page.content.map { it.id }).containsExactly(newer.id, older.id)
        assertThat(runs.existsByFeedAndStatus("customers", SyncRunStatus.RUNNING)).isTrue()
    }

    @Test
    fun `quarantined records keep the raw payload and are counted per feed`() {
        val run = runs.save(run("customers", Instant.now()))
        quarantine.save(
            QuarantinedRecordEntity(
                feed = "customers",
                businessKey = "cus_42",
                runId = run.id,
                reason = QuarantineReason.DRIFT,
                details = "field 'email' missing",
                payload = """{"id":"cus_42","emailAddress":"a@b.c"}""",
                sourceUpdatedAt = Instant.now(),
            ),
        )

        assertThat(quarantine.countByFeedAndStatus("customers", QuarantineStatus.OPEN)).isEqualTo(1)
        val open = quarantine.findByFeedAndBusinessKeyAndStatus("customers", "cus_42", QuarantineStatus.OPEN)
        assertThat(open).hasSize(1)
        assertThat(open.single().payload).contains("emailAddress")
    }

    @Test
    fun `an open drift event is found by fingerprint`() {
        val runId = UUID.randomUUID()
        drift.save(
            DriftEventEntity(
                feed = "customers",
                fingerprint = "FIELD_RENAMED:email->emailAddress",
                kind = DriftKind.FIELD_RENAMED,
                field = "email",
                expected = "email",
                actual = "emailAddress",
                details = "expected field 'email' missing, unexpected 'emailAddress' present",
                affectedRecords = 3,
                firstRunId = runId,
                lastRunId = runId,
            ),
        )

        val found =
            drift.findByFeedAndFingerprintAndStatus(
                "customers",
                "FIELD_RENAMED:email->emailAddress",
                DriftStatus.OPEN,
            )

        assertThat(found).isNotNull
        assertThat(found!!.affectedRecords).isEqualTo(3)
        assertThat(drift.countByFeedAndStatus("customers", DriftStatus.OPEN)).isEqualTo(1)
    }

    private fun run(
        feed: String,
        startedAt: Instant,
    ) = SyncRunEntity(
        feed = feed,
        mode = SyncMode.INCREMENTAL,
        trigger = SyncTrigger.MANUAL,
        startedAt = startedAt,
    )
}
