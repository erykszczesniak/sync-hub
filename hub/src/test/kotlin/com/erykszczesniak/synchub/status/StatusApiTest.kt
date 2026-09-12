package com.erykszczesniak.synchub.status

import com.erykszczesniak.synchub.repository.DriftEventEntity
import com.erykszczesniak.synchub.repository.DriftEventRepository
import com.erykszczesniak.synchub.repository.DriftKind
import com.erykszczesniak.synchub.repository.DriftStatus
import com.erykszczesniak.synchub.repository.QuarantineReason
import com.erykszczesniak.synchub.repository.QuarantinedRecordEntity
import com.erykszczesniak.synchub.repository.QuarantinedRecordRepository
import com.erykszczesniak.synchub.repository.SyncMode
import com.erykszczesniak.synchub.repository.SyncRunEntity
import com.erykszczesniak.synchub.repository.SyncRunRepository
import com.erykszczesniak.synchub.repository.SyncRunStatus
import com.erykszczesniak.synchub.repository.SyncTrigger
import com.erykszczesniak.synchub.sync.Watermark
import com.erykszczesniak.synchub.sync.WatermarkStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusApiTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var runs: SyncRunRepository

    @Autowired
    private lateinit var quarantine: QuarantinedRecordRepository

    @Autowired
    private lateinit var drift: DriftEventRepository

    @Autowired
    private lateinit var watermarks: WatermarkStore

    private val now = Instant.now()

    @BeforeEach
    @AfterEach
    fun clean() {
        runs.deleteAll()
        quarantine.deleteAll()
        drift.deleteAll()
        watermarks.reset()
    }

    @Test
    fun `feed health combines last run, freshness, watermark, drift and quarantine`() {
        val ok = run("orders", SyncRunStatus.SUCCEEDED, now.minusSeconds(30), loaded = 7, lag = 12)
        runs.save(ok)
        watermarks.advance("orders", Watermark(now.minusSeconds(40), "ord_9"))

        val failed = run("customers", SyncRunStatus.FAILED, now.minusSeconds(10))
        runs.save(run("customers", SyncRunStatus.SUCCEEDED, now.minusSeconds(3600)))
        runs.save(failed)

        mockMvc.get("/api/status/feeds").andExpect {
            status { isOk() }
            jsonPath("$[?(@.feed=='orders')].health") { value("HEALTHY") }
            jsonPath("$[?(@.feed=='orders')].protocol") { value("GraphQL") }
            jsonPath("$[?(@.feed=='orders')].lagSeconds") { value(12) }
            jsonPath("$[?(@.feed=='orders')].lastRun.loaded") { value(7) }
            jsonPath("$[?(@.feed=='customers')].health") { value("FAILING") }
            jsonPath("$[?(@.feed=='customers')].lastSuccessfulRun.status") { value("SUCCEEDED") }
        }
        mockMvc.get("/api/status/feeds/orders").andExpect {
            status { isOk() }
            jsonPath("$.watermark") { exists() }
            jsonPath("$.freshnessSeconds") { isNumber() }
        }
        mockMvc.get("/api/status/feeds/invoices").andExpect { status { isNotFound() } }
    }

    @Test
    fun `drift makes a feed DRIFT, silence makes it STALE, no runs makes it NEVER_RUN`() {
        runs.save(run("customers", SyncRunStatus.PARTIAL, now.minusSeconds(5)))
        runs.save(run("customers", SyncRunStatus.SUCCEEDED, now.minusSeconds(6)))
        drift.save(driftEvent("customers", now.minusSeconds(5)))
        runs.save(run("orders", SyncRunStatus.SUCCEEDED, now.minusSeconds(3600)))

        mockMvc.get("/api/status/feeds").andExpect {
            jsonPath("$[?(@.feed=='customers')].health") { value("DRIFT") }
            jsonPath("$[?(@.feed=='customers')].openDriftEvents") { value(1) }
            jsonPath("$[?(@.feed=='orders')].health") { value("STALE") }
        }

        clean()
        mockMvc.get("/api/status/feeds/orders").andExpect { jsonPath("$.health") { value("NEVER_RUN") } }
    }

    @Test
    fun `overview aggregates totals and computes MTTD and MTTR from drift events`() {
        runs.save(run("customers", SyncRunStatus.SUCCEEDED, now.minusSeconds(100), loaded = 10))
        runs.save(run("customers", SyncRunStatus.PARTIAL, now.minusSeconds(50), loaded = 2, quarantined = 3))
        val resolved =
            driftEvent("customers", detectedAt = now.minusSeconds(600), sourceChangedAt = now.minusSeconds(720)).apply {
                status = DriftStatus.RESOLVED
                resolvedAt = now.minusSeconds(300)
            }
        drift.save(resolved)
        drift.save(driftEvent("orders", detectedAt = now.minusSeconds(60), sourceChangedAt = now.minusSeconds(120)))
        quarantine.save(quarantined("customers"))

        mockMvc.get("/api/status/overview").andExpect {
            status { isOk() }
            jsonPath("$.totals.runs") { value(2) }
            jsonPath("$.totals.succeeded") { value(1) }
            jsonPath("$.totals.partial") { value(1) }
            jsonPath("$.totals.loaded") { value(12) }
            jsonPath("$.totals.quarantined") { value(3) }
            jsonPath("$.totals.openQuarantine") { value(1) }
            jsonPath("$.totals.openDriftEvents") { value(1) }
            jsonPath("$.mttdSeconds") { value(90) }
            jsonPath("$.mttrSeconds") { value(300) }
            jsonPath("$.feeds.length()") { value(2) }
        }
    }

    @Test
    fun `runs, quarantine and drift are paged newest first`() {
        runs.save(run("customers", SyncRunStatus.SUCCEEDED, now.minusSeconds(20)))
        val newest = runs.save(run("customers", SyncRunStatus.SUCCEEDED, now.minusSeconds(10)))
        runs.save(run("orders", SyncRunStatus.SUCCEEDED, now.minusSeconds(5)))
        quarantine.save(quarantined("customers"))
        drift.save(driftEvent("customers", now))

        mockMvc.get("/api/status/runs?feed=customers&size=1").andExpect {
            status { isOk() }
            jsonPath("$.content[0].id") { value(newest.id.toString()) }
            jsonPath("$.totalElements") { value(2) }
            jsonPath("$.totalPages") { value(2) }
        }
        mockMvc.get("/api/status/runs/${newest.id}").andExpect { jsonPath("$.feed") { value("customers") } }
        mockMvc.get("/api/status/runs/${UUID.randomUUID()}").andExpect { status { isNotFound() } }
        mockMvc.get("/api/status/quarantine").andExpect {
            jsonPath("$.content[0].payload") { value("""{"id":"cus_1"}""") }
            jsonPath("$.content[0].reason") { value("DRIFT") }
        }
        mockMvc.get("/api/status/quarantine?status=DISCARDED").andExpect { jsonPath("$.totalElements") { value(0) } }
        mockMvc.get("/api/status/drift?status=OPEN").andExpect {
            jsonPath("$.content[0].kind") { value("FIELD_RENAMED") }
            jsonPath("$.content[0].severity") { value("BLOCKING") }
            jsonPath("$.content[0].timeToDetectSeconds") { value(60) }
        }
        mockMvc.get("/api/status/runs?size=999").andExpect { status { isBadRequest() } }
    }

    private fun run(
        feed: String,
        status: SyncRunStatus,
        finishedAt: Instant,
        loaded: Int = 0,
        quarantined: Int = 0,
        lag: Long? = null,
    ) = SyncRunEntity(
        feed = feed,
        mode = SyncMode.INCREMENTAL,
        trigger = SyncTrigger.SCHEDULED,
        startedAt = finishedAt.minusSeconds(1),
    ).apply {
        this.status = status
        this.finishedAt = finishedAt
        this.loaded = loaded
        this.quarantined = quarantined
        this.maxLagSeconds = lag
    }

    private fun driftEvent(
        feed: String,
        detectedAt: Instant,
        sourceChangedAt: Instant = detectedAt.minusSeconds(60),
    ) = DriftEventEntity(
        feed = feed,
        fingerprint = "FIELD_RENAMED:email:email->emailAddress",
        kind = DriftKind.FIELD_RENAMED,
        field = "email",
        expected = "email",
        actual = "emailAddress",
        details = "renamed",
        affectedRecords = 2,
        firstRunId = UUID.randomUUID(),
        lastRunId = UUID.randomUUID(),
        sourceChangedAt = sourceChangedAt,
        detectedAt = detectedAt,
        lastSeenAt = detectedAt,
    )

    private fun quarantined(feed: String) =
        QuarantinedRecordEntity(
            feed = feed,
            businessKey = "cus_1",
            runId = UUID.randomUUID(),
            reason = QuarantineReason.DRIFT,
            details = "renamed",
            payload = """{"id":"cus_1"}""",
            sourceUpdatedAt = now,
        )
}
