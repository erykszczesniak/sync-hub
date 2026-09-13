package com.erykszczesniak.systema

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class SystemASimApplicationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `context starts and health endpoint answers`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
    }

    @Test
    fun `openapi documents the change-feed query parameters from the parameter object`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/customers'].get.parameters[*].name") {
                value(org.hamcrest.Matchers.containsInAnyOrder("updatedSince", "updatedUntil", "cursor", "limit"))
            }
        }
    }
}
