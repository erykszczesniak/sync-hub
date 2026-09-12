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
}
