package com.erykszczesniak.systema.graphql

import com.erykszczesniak.systema.drift.DriftScenario
import com.erykszczesniak.systema.drift.DriftState
import com.erykszczesniak.systema.store.DataSeeder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.graphql.client.HttpGraphQlClient
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["system-a.api-key=test-key"])
class OrdersGraphQlTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var seeder: DataSeeder

    @Autowired
    private lateinit var driftState: DriftState

    private lateinit var tester: HttpGraphQlTester

    @BeforeEach
    fun setUp() {
        driftState.clear()
        seeder.seed(customerCount = 3, orderCount = 5)
        val client =
            WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:$port/graphql")
                .defaultHeader("X-API-Key", "test-key")
                .build()
        tester = HttpGraphQlTester.create(client)
    }

    @Test
    fun `orders are paged as a connection`() {
        val first =
            tester
                .document(QUERY)
                .variable("first", 2)
                .execute()
        val ids = first.path("orders.edges[*].node.id").entityList(String::class.java).get()
        assertThat(ids).hasSize(2)
        first.path("orders.pageInfo.hasNextPage").entity(Boolean::class.java).isEqualTo(true)
        val cursor = first.path("orders.pageInfo.endCursor").entity(String::class.java).get()

        val rest =
            tester
                .document(QUERY)
                .variable("first", 10)
                .variable("after", cursor)
                .execute()
        val restIds = rest.path("orders.edges[*].node.id").entityList(String::class.java).get()
        assertThat(restIds).hasSize(3).doesNotContainAnyElementsOf(ids)
        rest.path("orders.pageInfo.hasNextPage").entity(Boolean::class.java).isEqualTo(false)
    }

    @Test
    fun `money amounts are decimal strings and status drift produces an unknown value`() {
        tester
            .document(QUERY)
            .variable("first", 1)
            .execute()
            .path("orders.edges[0].node.total.amount")
            .entity(String::class.java)
            .matches { it.matches(Regex("\\d+\\.\\d{2}")) }

        driftState.replace(setOf(DriftScenario.ORDER_STATUS_ON_HOLD))
        tester
            .document(QUERY)
            .variable("first", 1)
            .execute()
            .path("orders.edges[0].node.status")
            .entity(String::class.java)
            .isEqualTo("ON_HOLD")
    }

    @Test
    fun `graphql endpoint requires the api key`() {
        val anonymous =
            HttpGraphQlTester.create(WebTestClient.bindToServer().baseUrl("http://localhost:$port/graphql").build())

        val error =
            org.junit.jupiter.api.assertThrows<AssertionError> {
                anonymous.document(QUERY).variable("first", 1).execute()
            }

        assertThat(error.message).contains("401")
        // The client type is also what the hub will use, so make sure it is on the classpath.
        assertThat(HttpGraphQlClient::class.java.simpleName).isEqualTo("HttpGraphQlClient")
    }

    companion object {
        private const val QUERY =
            """
            query Orders(${'$'}updatedSince: String, ${'$'}after: String, ${'$'}first: Int) {
              orders(updatedSince: ${'$'}updatedSince, after: ${'$'}after, first: ${'$'}first) {
                edges { cursor node { id customerId status total { amount currency } lines { sku quantity unitPrice } placedAt updatedAt deleted } }
                pageInfo { endCursor hasNextPage }
              }
            }
            """
    }
}
