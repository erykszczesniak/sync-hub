package com.erykszczesniak.synchub.status

import com.erykszczesniak.synchub.repository.QuarantineReason
import com.erykszczesniak.synchub.repository.QuarantineStatus
import com.erykszczesniak.synchub.repository.QuarantinedRecordEntity
import com.erykszczesniak.synchub.repository.QuarantinedRecordRepository
import com.erykszczesniak.synchub.repository.SyncMode
import com.erykszczesniak.synchub.repository.SyncRunEntity
import com.erykszczesniak.synchub.repository.SyncTrigger
import com.erykszczesniak.synchub.sync.SyncOrchestrator
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.AfterEach
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
import java.util.UUID

@SpringBootTest(properties = ["synchub.security.admin.username=op", "synchub.security.admin.password=secret"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RemediationControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var quarantine: QuarantinedRecordRepository

    @MockkBean
    private lateinit var orchestrator: SyncOrchestrator

    @AfterEach
    fun clean() = quarantine.deleteAll()

    @Test
    fun `replay needs credentials and returns the replay run`() {
        val id = UUID.randomUUID()
        every { orchestrator.replay(id) } returns
            SyncRunEntity(feed = "customers", mode = SyncMode.REPLAY, trigger = SyncTrigger.MANUAL)

        mockMvc.post("/api/quarantine/$id/replay").andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/quarantine/$id/replay") { with(httpBasic("op", "secret")) }.andExpect {
            status { isAccepted() }
            jsonPath("$.mode") { value("REPLAY") }
        }
    }

    @Test
    fun `discard closes a quarantined record with a note and unknown ids are 404`() {
        val entry =
            quarantine.save(
                QuarantinedRecordEntity(
                    feed = "customers",
                    businessKey = "cus_1",
                    runId = UUID.randomUUID(),
                    reason = QuarantineReason.VALIDATION,
                    details = "bad email",
                    payload = "{}",
                    sourceUpdatedAt = Instant.now(),
                ),
            )

        mockMvc
            .post("/api/quarantine/${entry.id}/discard") {
                with(httpBasic("op", "secret"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"note":"test data, not a real customer"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("DISCARDED") }
                jsonPath("$.resolutionNote") { value("test data, not a real customer") }
            }
        org.assertj.core.api.Assertions
            .assertThat(quarantine.findById(entry.id).orElseThrow().status)
            .isEqualTo(QuarantineStatus.DISCARDED)
        mockMvc
            .post("/api/quarantine/${UUID.randomUUID()}/discard") {
                with(httpBasic("op", "secret"))
            }.andExpect { status { isNotFound() } }
        mockMvc
            .post("/api/drift/${UUID.randomUUID()}/resolve") {
                with(httpBasic("op", "secret"))
            }.andExpect { status { isNotFound() } }
    }
}
