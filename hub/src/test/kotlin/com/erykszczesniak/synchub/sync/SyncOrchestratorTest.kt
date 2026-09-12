package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.repository.DriftEventRepository
import com.erykszczesniak.synchub.repository.DriftStatus
import com.erykszczesniak.synchub.repository.OutboxEventRepository
import com.erykszczesniak.synchub.repository.QuarantineStatus
import com.erykszczesniak.synchub.repository.QuarantinedRecordRepository
import com.erykszczesniak.synchub.repository.SyncRunRepository
import com.erykszczesniak.synchub.repository.SyncRunStatus
import com.erykszczesniak.synchub.repository.SyncTrigger
import com.erykszczesniak.synchub.repository.SystemBCustomerRepository
import com.erykszczesniak.synchub.repository.SystemBOrderRepository
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant

/**
 * The whole hub against a stubbed System A: incremental sync, idempotent re-run, drift quarantine,
 * backfill, failure.
 */
@SpringBootTest(
    properties = [
        "resilience4j.retry.instances.system-a.wait-duration=10ms",
        "resilience4j.retry.instances.system-a.enable-exponential-backoff=false",
        "synchub.source.system-a.page-size=2",
        "synchub.sync.watermark-overlap=0s",
    ],
)
@ActiveProfiles("test")
class SyncOrchestratorTest {
    @Autowired
    private lateinit var orchestrator: SyncOrchestrator

    @Autowired
    private lateinit var runs: SyncRunRepository

    @Autowired
    private lateinit var customers: SystemBCustomerRepository

    @Autowired
    private lateinit var orders: SystemBOrderRepository

    @Autowired
    private lateinit var quarantine: QuarantinedRecordRepository

    @Autowired
    private lateinit var drift: DriftEventRepository

    @Autowired
    private lateinit var outbox: OutboxEventRepository

    @Autowired
    private lateinit var watermarks: WatermarkStore

    @BeforeEach
    @org.junit.jupiter.api.AfterEach
    fun clean() {
        wireMock.resetAll()
        runs.deleteAll()
        customers.deleteAll()
        orders.deleteAll()
        quarantine.deleteAll()
        drift.deleteAll()
        outbox.deleteAll()
        watermarks.reset()
    }

    @Test
    fun `first run loads everything, second run re-reads nothing new and skips, a change is picked up incrementally`() {
        stubCustomers(
            page1 = listOf(customer("cus_1", T1), customer("cus_2", T2)),
            page2 = listOf(customer("cus_3", T3)),
        )

        val first = orchestrator.runIncremental("customers", SyncTrigger.MANUAL)

        assertThat(first.status).isEqualTo(SyncRunStatus.SUCCEEDED)
        assertThat(first.extracted).isEqualTo(3)
        assertThat(first.loaded).isEqualTo(3)
        assertThat(first.skipped).isZero()
        assertThat(first.watermarkBefore).isNull()
        assertThat(first.watermarkAfter).isEqualTo(Instant.parse(T3))
        assertThat(customers.count()).isEqualTo(3)
        assertThat(outbox.count()).isEqualTo(3)

        // Nothing changed: the source answers with the same records inside the window → skipped, no events.
        wireMock.resetAll()
        stubCustomers(page1 = listOf(customer("cus_3", T3)), since = T3)
        val second = orchestrator.runIncremental("customers", SyncTrigger.SCHEDULED)
        assertThat(second.status).isEqualTo(SyncRunStatus.SUCCEEDED)
        assertThat(second.loaded).isZero()
        assertThat(second.skipped).isEqualTo(1)
        assertThat(outbox.count()).isEqualTo(3)
        assertThat(second.watermarkAfter).isEqualTo(Instant.parse(T3))

        // One customer changed after the watermark → one UPDATED and the watermark moves.
        wireMock.resetAll()
        stubCustomers(page1 = listOf(customer("cus_2", T4, tier = "PLATINUM")), since = T3)
        val third = orchestrator.runIncremental("customers", SyncTrigger.SCHEDULED)
        assertThat(third.loaded).isEqualTo(1)
        assertThat(customers.findByBusinessKey("cus_2")!!.tierRank).isEqualTo(4)
        assertThat(customers.findByBusinessKey("cus_2")!!.version).isEqualTo(2)
        assertThat(third.watermarkAfter).isEqualTo(Instant.parse(T4))
        assertThat(outbox.count()).isEqualTo(4)
        assertThat(third.maxLagSeconds).isNotNull()
    }

    @Test
    fun `blocking drift quarantines, raises one event, marks the run partial and still moves the watermark`() {
        val drifted =
            listOf(
                customer("cus_1", T1).replace("\"email\"", "\"emailAddress\""),
                customer("cus_2", T2).replace("\"email\"", "\"emailAddress\""),
            )
        stubCustomers(page1 = drifted)

        val run = orchestrator.runIncremental("customers", SyncTrigger.MANUAL)

        assertThat(run.status).isEqualTo(SyncRunStatus.PARTIAL)
        assertThat(run.quarantined).isEqualTo(2)
        assertThat(run.loaded).isZero()
        assertThat(run.driftDetected).isTrue()
        assertThat(run.watermarkAfter).isEqualTo(Instant.parse(T2))
        assertThat(customers.count()).isZero()
        assertThat(quarantine.countByFeedAndStatus("customers", QuarantineStatus.OPEN)).isEqualTo(2)
        val events = drift.findByFeedAndStatus("customers", DriftStatus.OPEN)
        assertThat(events).hasSize(1)
        assertThat(events.single().affectedRecords).isEqualTo(2)
        assertThat(events.single().sourceChangedAt).isEqualTo(Instant.parse(T1))

        // The source is fixed and a backfill of the window re-syncs the records: quarantine closed, drift resolved.
        wireMock.resetAll()
        stubCustomers(
            page1 = listOf(customer("cus_1", T1), customer("cus_2", T2)),
            since = "2026-01-01T00:00:00Z",
            until = T2,
        )
        val backfill = orchestrator.runBackfill("customers", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse(T2))
        assertThat(backfill.status).isEqualTo(SyncRunStatus.SUCCEEDED)
        assertThat(backfill.loaded).isEqualTo(2)
        assertThat(backfill.watermarkAfter).isEqualTo(Instant.parse(T2))
        assertThat(quarantine.countByFeedAndStatus("customers", QuarantineStatus.OPEN)).isZero()
        assertThat(drift.findByFeedAndStatus("customers", DriftStatus.OPEN)).isEmpty()
        assertThat(drift.findAll().single().resolutionNote).contains("clean backfill")
    }

    @Test
    fun `an added field is loaded with a warning event`() {
        stubCustomers(
            page1 = listOf(customer("cus_1", T1).replace("\"deleted\":false", "\"deleted\":false,\"loyaltyPoints\":5")),
        )

        val run = orchestrator.runIncremental("customers", SyncTrigger.MANUAL)

        assertThat(run.status).isEqualTo(SyncRunStatus.SUCCEEDED)
        assertThat(run.loaded).isEqualTo(1)
        assertThat(run.driftDetected).isTrue()
        assertThat(drift.findByFeedAndStatus("customers", DriftStatus.OPEN).single().field).isEqualTo("loyaltyPoints")
    }

    @Test
    fun `a source outage fails the run and leaves the watermark alone`() {
        stubCustomers(page1 = listOf(customer("cus_1", T1)))
        orchestrator.runIncremental("customers", SyncTrigger.MANUAL)
        wireMock.resetAll()
        wireMock.stubFor(get(urlPathEqualTo("/api/customers")).willReturn(aResponse().withStatus(503)))

        val run = orchestrator.runIncremental("customers", SyncTrigger.SCHEDULED)

        assertThat(run.status).isEqualTo(SyncRunStatus.FAILED)
        assertThat(run.errorMessage).contains("503")
        assertThat(run.watermarkAfter).isEqualTo(Instant.parse(T1))
        assertThat(watermarks.read("customers")!!.timestamp).isEqualTo(Instant.parse(T1))
    }

    @Test
    fun `orders flow through the GraphQL path and a tombstone soft-deletes`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/graphql")).willReturn(
                okJson(
                    """{"data":{"orders":{"edges":[{"node":${order(
                        "ord_1",
                        T1,
                        deleted = false,
                    )}},{"node":${order("ord_1", T2, deleted = true)}}],
                       "pageInfo":{"endCursor":null,"hasNextPage":false}}}}""",
                ),
            ),
        )

        val run = orchestrator.runIncremental("orders", SyncTrigger.MANUAL)

        assertThat(run.status).isEqualTo(SyncRunStatus.SUCCEEDED)
        assertThat(run.loaded).isEqualTo(2)
        val stored = orders.findByBusinessKey("ord_1")!!
        assertThat(stored.deletedAt).isNotNull()
        assertThat(outbox.findByFeedAndBusinessKeyOrderByCreatedAtAsc("orders", "ord_1").map { it.eventType.name })
            .containsExactly("CREATED", "DELETED")
    }

    @Test
    fun `a quarantined record can be replayed once the cause is fixed`() {
        stubCustomers(page1 = listOf(customer("cus_1", T1).replace("\"country\":\"PL\"", "\"country\":\"Poland\"")))
        orchestrator.runIncremental("customers", SyncTrigger.MANUAL)
        val entry = quarantine.findByFeedAndBusinessKeyAndStatus("customers", "cus_1", QuarantineStatus.OPEN).single()
        assertThat(entry.reason.name).isEqualTo("VALIDATION")

        // Same payload again: still invalid → re-quarantined, the old entry superseded, nothing loaded.
        val stillBroken = orchestrator.replay(entry.id)
        assertThat(stillBroken.mode.name).isEqualTo("REPLAY")
        assertThat(stillBroken.quarantined).isEqualTo(1)
        assertThat(quarantine.findById(entry.id).orElseThrow().status).isEqualTo(QuarantineStatus.SUPERSEDED)
        val reopened =
            quarantine
                .findByFeedAndBusinessKeyAndStatus(
                    "customers",
                    "cus_1",
                    QuarantineStatus.OPEN,
                ).single()

        // Operator fixes the stored payload's cause upstream; here the source resends a valid version via backfill,
        // which supersedes the open quarantine because the loaded version is at least as new.
        wireMock.resetAll()
        stubCustomers(page1 = listOf(customer("cus_1", T1)), since = "2026-01-01T00:00:00Z", until = T1)
        orchestrator.runBackfill("customers", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse(T1))
        assertThat(quarantine.findById(reopened.id).orElseThrow().status).isEqualTo(QuarantineStatus.SUPERSEDED)
        assertThat(customers.findByBusinessKey("cus_1")).isNotNull()
        assertThrows<IllegalArgumentException> { orchestrator.replay(reopened.id) }
    }

    @Test
    fun `unknown feeds are rejected and a backfill window must be ordered`() {
        assertThrows<UnknownFeedException> { orchestrator.runIncremental("invoices", SyncTrigger.MANUAL) }
        assertThrows<IllegalArgumentException> {
            orchestrator.runBackfill(
                "customers",
                Instant.parse(T2),
                Instant.parse(T1),
            )
        }
    }

    private fun stubCustomers(
        page1: List<String>,
        page2: List<String> = emptyList(),
        since: String? = null,
        until: String? = null,
    ) {
        val hasMore = page2.isNotEmpty()
        var first =
            get(
                urlPathEqualTo("/api/customers"),
            ).withQueryParam(
                "cursor",
                com.github.tomakehurst.wiremock.client.WireMock
                    .absent(),
            )
        if (since != null) first = first.withQueryParam("updatedSince", equalTo(since))
        if (until != null) first = first.withQueryParam("updatedUntil", equalTo(until))
        wireMock.stubFor(
            first.willReturn(
                okJson(
                    """{"items":[${page1.joinToString(
                        ",",
                    )}],"nextCursor":${if (hasMore) "\"p2\"" else "null"},"hasMore":$hasMore}""",
                ),
            ),
        )
        if (hasMore) {
            wireMock.stubFor(
                get(urlPathEqualTo("/api/customers"))
                    .withQueryParam("cursor", equalTo("p2"))
                    .willReturn(okJson("""{"items":[${page2.joinToString(",")}],"nextCursor":null,"hasMore":false}""")),
            )
        }
    }

    companion object {
        private const val T1 = "2026-01-10T10:00:00Z"
        private const val T2 = "2026-01-10T11:00:00Z"
        private const val T3 = "2026-01-10T12:00:00Z"
        private const val T4 = "2026-01-10T13:00:00Z"

        private val wireMock = WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun sourceUrl(registry: DynamicPropertyRegistry) {
            registry.add("synchub.source.system-a.base-url") { wireMock.baseUrl() }
        }

        @JvmStatic
        @AfterAll
        fun stop() = wireMock.stop()

        private fun okJson(body: String) =
            aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)

        fun customer(
            id: String,
            updatedAt: String,
            tier: String = "GOLD",
        ) =
            """{"id":"$id","email":"$id@example.com","firstName":"First","lastName":"Last","status":"ACTIVE","tier":"$tier",
               "address":{"line1":"1 Street","city":"Warsaw","postalCode":"00-001","country":"PL"},"tags":["vip"],
               "createdAt":"2026-01-01T00:00:00Z","updatedAt":"$updatedAt","deleted":false}"""

        fun order(
            id: String,
            updatedAt: String,
            deleted: Boolean,
        ) = """{"id":"$id","customerId":"cus_1","status":"PAID","total":{"amount":"10.00","currency":"EUR"},
               "lines":[{"sku":"S","quantity":1,"unitPrice":"10.00"}],"placedAt":"2026-01-01T00:00:00Z",
               "updatedAt":"$updatedAt","deleted":$deleted}"""
    }
}
