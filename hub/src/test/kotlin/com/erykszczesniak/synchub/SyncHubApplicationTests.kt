package com.erykszczesniak.synchub

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncHubApplicationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `context starts and health endpoint is open`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
    }

    @Test
    fun `openapi document is served without credentials`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.info.title") { value("sync-hub API") }
            jsonPath("$.paths['/api/status/runs'].get.parameters[*].name") {
                value(org.hamcrest.Matchers.containsInAnyOrder("feed", "page", "size"))
            }
        }
    }

    @Test
    fun `state-changing endpoints require authentication`() {
        mockMvc.post("/api/sync/customers/run").andExpect { status { isUnauthorized() } }
    }
}
