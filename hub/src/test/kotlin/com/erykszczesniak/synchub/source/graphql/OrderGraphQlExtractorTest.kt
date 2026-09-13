package com.erykszczesniak.synchub.source.graphql

import com.erykszczesniak.synchub.common.SourceAuthException
import com.erykszczesniak.synchub.common.SourceResponseException
import com.erykszczesniak.synchub.common.SourceUnavailableException
import com.erykszczesniak.synchub.source.ExtractionWindow
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.Scenario
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

@SpringBootTest(
    properties = [
        "resilience4j.retry.instances.system-a.wait-duration=10ms",
        "resilience4j.retry.instances.system-a.enable-exponential-backoff=false",
        "synchub.source.system-a.api-key=secret-key",
    ],
)
@ActiveProfiles("test")
class OrderGraphQlExtractorTest {
    @Autowired
    private lateinit var extractor: OrderGraphQlExtractor

    @BeforeEach
    fun resetStubs() {
        wireMock.resetAll()
    }

    @Test
    fun `posts the connection query with variables and pages with the end cursor`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/graphql"))
                .withHeader("X-API-Key", equalTo("secret-key"))
                .withRequestBody(matchingJsonPath("$.variables.updatedSince", equalTo("2026-01-01T00:00:00Z")))
                .withRequestBody(matchingJsonPath("$.variables.first", equalTo("2")))
                .willReturn(
                    okJson(
                        """{"data":{"orders":{"edges":[
                          {"node":{"id":"ord_1","customerId":"cus_1","status":"PAID","total":{"amount":"10.00","currency":"EUR"},
                                   "lines":[{"sku":"S","quantity":1,"unitPrice":"10.00"}],
                                   "placedAt":"2026-01-01T09:00:00Z","updatedAt":"2026-01-01T10:00:00Z","deleted":false}},
                          {"node":{"id":"ord_2","customerId":"cus_1","status":"CANCELLED","total":{"amount":"0.00","currency":"EUR"},
                                   "lines":[],"placedAt":"2026-01-01T09:00:00Z","updatedAt":"2026-01-01T11:00:00Z","deleted":true}}
                        ],"pageInfo":{"endCursor":"c2","hasNextPage":true}}}}""",
                    ),
                ),
        )
        wireMock.stubFor(
            post(urlPathEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.variables.after", equalTo("c2")))
                .willReturn(
                    okJson(
                        """{"data":{"orders":{"edges":[{"node":{"id":"ord_3","updatedAt":"2026-01-01T12:00:00Z","deleted":false}}],
                           "pageInfo":{"endCursor":null,"hasNextPage":false}}}}""",
                    ),
                ),
        )

        val pages =
            extractor
                .pages(
                    ExtractionWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
                    pageSize = 2,
                    maxPages = 10,
                ).toList()

        val records = pages.flatMap { it.records }
        assertThat(records.map { it.businessKey }).containsExactly("ord_1", "ord_2", "ord_3")
        assertThat(records[0].payload["total"]["amount"].asText()).isEqualTo("10.00")
        assertThat(records[1].deleted).isTrue()
        assertThat(records).allMatch { it.feed == "orders" }
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/graphql")))
    }

    @Test
    fun `graphql errors are a response error and are not retried`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/graphql"))
                .willReturn(okJson("""{"errors":[{"message":"Cannot query field 'orders'"}],"data":null}""")),
        )

        val ex =
            assertThrows<SourceResponseException> { extractor.fetchChanges(ExtractionWindow(null, null), null, 10) }

        assertThat(ex.message).contains("Cannot query field")
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/graphql")))
    }

    @Test
    fun `transport failures are retried and auth failures are not`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/graphql"))
                .inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("up"),
        )
        wireMock.stubFor(
            post(urlPathEqualTo("/graphql"))
                .inScenario("flaky")
                .whenScenarioStateIs("up")
                .willReturn(
                    okJson("""{"data":{"orders":{"edges":[],"pageInfo":{"endCursor":null,"hasNextPage":false}}}}"""),
                ),
        )
        assertThat(extractor.fetchChanges(ExtractionWindow(null, null), null, 10).records).isEmpty()
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/graphql")))

        wireMock.resetAll()
        wireMock.stubFor(post(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(401)))
        assertThrows<SourceAuthException> { extractor.fetchChanges(ExtractionWindow(null, null), null, 10) }
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/graphql")))

        wireMock.resetAll()
        wireMock.stubFor(post(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(500)))
        assertThrows<SourceUnavailableException> { extractor.fetchChanges(ExtractionWindow(null, null), null, 10) }
        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/graphql")))
    }

    companion object {
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
    }
}
