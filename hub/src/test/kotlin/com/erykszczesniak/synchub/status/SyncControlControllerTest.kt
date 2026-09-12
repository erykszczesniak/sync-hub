package com.erykszczesniak.synchub.status

import com.erykszczesniak.synchub.repository.SyncMode
import com.erykszczesniak.synchub.repository.SyncRunEntity
import com.erykszczesniak.synchub.repository.SyncRunStatus
import com.erykszczesniak.synchub.repository.SyncTrigger
import com.erykszczesniak.synchub.sync.FeedBusyException
import com.erykszczesniak.synchub.sync.SyncOrchestrator
import com.erykszczesniak.synchub.sync.UnknownFeedException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant

@SpringBootTest(properties = ["synchub.security.admin.username=op", "synchub.security.admin.password=secret"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncControlControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var orchestrator: SyncOrchestrator

    @Test
    fun `triggering a sync needs the operator account and returns the finished run`() {
        every { orchestrator.runIncremental("customers", SyncTrigger.MANUAL) } returns
            SyncRunEntity(feed = "customers", mode = SyncMode.INCREMENTAL, trigger = SyncTrigger.MANUAL).apply {
                status = SyncRunStatus.SUCCEEDED
                finishedAt = Instant.now()
                extracted = 5
                loaded = 4
                skipped = 1
            }

        mockMvc.post("/api/sync/customers/run").andExpect { status { isUnauthorized() } }
        mockMvc
            .post("/api/sync/customers/run") { with(httpBasic("op", "secret")) }
            .andExpect {
                status { isAccepted() }
                jsonPath("$.status") { value("SUCCEEDED") }
                jsonPath("$.loaded") { value(4) }
                jsonPath("$.durationMs") { isNumber() }
            }
    }

    @Test
    fun `problems are reported as problem details`() {
        every { orchestrator.runIncremental("invoices", any()) } throws UnknownFeedException("invoices")
        every { orchestrator.runIncremental("orders", any()) } throws FeedBusyException("orders")
        every { orchestrator.runBackfill("customers", any(), any(), any()) } throws
            IllegalArgumentException("backfill window end must not be before its start")

        mockMvc.post("/api/sync/invoices/run") { with(httpBasic("op", "secret")) }.andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("Unknown feed 'invoices'") }
        }
        mockMvc.post("/api/sync/orders/run") { with(httpBasic("op", "secret")) }.andExpect { status { isConflict() } }
        mockMvc
            .post("/api/sync/customers/backfill") {
                with(httpBasic("op", "secret"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"from":"2026-01-02T00:00:00Z","to":"2026-01-01T00:00:00Z"}"""
            }.andExpect { status { isBadRequest() } }
    }
}
