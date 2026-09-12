package com.erykszczesniak.systema.api

import com.erykszczesniak.systema.drift.DriftScenario
import com.erykszczesniak.systema.drift.DriftState
import com.erykszczesniak.systema.store.DataSeeder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put

@SpringBootTest(properties = ["system-a.api-key=test-key"])
@AutoConfigureMockMvc
class CustomersApiTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var seeder: DataSeeder

    @Autowired
    private lateinit var driftState: DriftState

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun reset() {
        driftState.clear()
        seeder.seed(customerCount = 7, orderCount = 0)
    }

    @Test
    fun `requests without the api key are rejected`() {
        mockMvc.get("/api/customers").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/customers") { header("X-API-Key", "wrong") }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `change feed pages through all customers with a cursor`() {
        val first = fetch("/api/customers?limit=3")
        assertThat(first.items).hasSize(3)
        assertThat(first.hasMore).isTrue()

        val second = fetch("/api/customers?limit=3&cursor=${first.nextCursor}")
        val third = fetch("/api/customers?limit=3&cursor=${second.nextCursor}")
        assertThat(third.items).hasSize(1)
        assertThat(third.hasMore).isFalse()

        val ids = (first.items + second.items + third.items).map { it["id"].asText() }
        assertThat(ids).doesNotHaveDuplicates().hasSize(7)
    }

    @Test
    fun `an edit bumps updatedAt and shows up in an incremental query`() {
        val latest = fetch("/api/customers?limit=100").items.maxOf { it["updatedAt"].asText() }
        val target = fetch("/api/customers?limit=1").items.single()["id"].asText()

        mockMvc
            .patch("/admin/customers/$target") {
                header("X-API-Key", "test-key")
                contentType = MediaType.APPLICATION_JSON
                content = """{"firstName":"Renamed"}"""
            }.andExpect { status { isOk() } }

        val changed = fetch("/api/customers?updatedSince=$latest").items
        assertThat(changed).hasSize(1)
        assertThat(changed.single()["id"].asText()).isEqualTo(target)
        assertThat(changed.single()["firstName"].asText()).isEqualTo("Renamed")
    }

    @Test
    fun `a delete is a tombstone in the feed`() {
        val latest = fetch("/api/customers?limit=100").items.maxOf { it["updatedAt"].asText() }
        val target = fetch("/api/customers?limit=1").items.single()["id"].asText()

        mockMvc.delete("/admin/customers/$target") { header("X-API-Key", "test-key") }.andExpect { status { isOk() } }

        val changed = fetch("/api/customers?updatedSince=$latest").items
        assertThat(changed.single()["deleted"].asBoolean()).isTrue()
    }

    @Test
    fun `drift scenarios change the wire format without touching stored data`() {
        mockMvc
            .put("/admin/drift") {
                header("X-API-Key", "test-key")
                contentType = MediaType.APPLICATION_JSON
                content = """{"scenarios":["RENAME_EMAIL","RETYPE_POSTAL_CODE","ADD_LOYALTY_POINTS","DROP_TIER"]}"""
            }.andExpect { status { isOk() } }

        val drifted = fetch("/api/customers?limit=1").items.single()
        assertThat(drifted.has("email")).isFalse()
        assertThat(drifted.has("emailAddress")).isTrue()
        assertThat(drifted["address"]["postalCode"].isNumber).isTrue()
        assertThat(drifted["loyaltyPoints"].asInt()).isEqualTo(120)
        assertThat(drifted.has("tier")).isFalse()

        driftState.replace(emptySet())
        assertThat(driftState.active()).isEmpty()
        val clean = fetch("/api/customers?limit=1").items.single()
        assertThat(clean.has("email")).isTrue()
        assertThat(clean["address"]["postalCode"].isTextual).isTrue()
        assertThat(driftState.isActive(DriftScenario.DROP_TIER)).isFalse()
    }

    private fun fetch(path: String): CustomerChangesResponse {
        val json =
            mockMvc
                .get(path) { header("X-API-Key", "test-key") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        return objectMapper.readValue(json)
    }
}
