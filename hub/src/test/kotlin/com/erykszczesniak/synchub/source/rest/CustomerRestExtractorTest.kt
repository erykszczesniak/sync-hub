package com.erykszczesniak.synchub.source.rest

import com.erykszczesniak.synchub.common.SourceAuthException
import com.erykszczesniak.synchub.common.SourceResponseException
import com.erykszczesniak.synchub.common.SourceUnavailableException
import com.erykszczesniak.synchub.source.ExtractionWindow
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
class CustomerRestExtractorTest {
    @Autowired
    private lateinit var extractor: CustomerRestExtractor

    @BeforeEach
    fun resetStubs() {
        wireMock.resetAll()
    }

    @Test
    fun `pages through the change feed, sends the api key and lifts key, version and tombstone out of each record`() {
        wireMock.stubFor(
            get(urlPathEqualTo("/api/customers"))
                .withHeader("X-API-Key", equalTo("secret-key"))
                .withQueryParam("updatedSince", equalTo("2026-01-01T00:00:00Z"))
                .withQueryParam("limit", equalTo("2"))
                .withQueryParam(
                    "cursor",
                    com.github.tomakehurst.wiremock.client.WireMock
                        .absent(),
                ).willReturn(
                    okJson(
                        """{"items":[
                          {"id":"cus_1","updatedAt":"2026-01-01T10:00:00Z","deleted":false,"email":"a@b.c"},
                          {"id":"cus_2","updatedAt":"2026-01-01T11:00:00Z","deleted":true}
                        ],"nextCursor":"c2","hasMore":true}""",
                    ),
                ),
        )
        wireMock.stubFor(
            get(urlPathEqualTo("/api/customers"))
                .withQueryParam("cursor", equalTo("c2"))
                .willReturn(
                    okJson(
                        """{"items":[{"id":"cus_3","updatedAt":"2026-01-01T12:00:00Z"}],""" +
                            """"nextCursor":null,"hasMore":false}""",
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

        assertThat(pages).hasSize(2)
        val records = pages.flatMap { it.records }
        assertThat(records.map { it.businessKey }).containsExactly("cus_1", "cus_2", "cus_3")
        assertThat(records[0].payload["email"].asText()).isEqualTo("a@b.c")
        assertThat(records[1].deleted).isTrue()
        assertThat(records[2].sourceUpdatedAt).isEqualTo(Instant.parse("2026-01-01T12:00:00Z"))
        assertThat(records).allMatch { it.feed == "customers" }
    }

    @Test
    fun `maxPages bounds a source that never stops`() {
        wireMock.stubFor(
            get(urlPathEqualTo("/api/customers"))
                .willReturn(
                    okJson(
                        """{"items":[{"id":"x","updatedAt":"2026-01-01T00:00:00Z"}],""" +
                            """"nextCursor":"again","hasMore":true}""",
                    ),
                ),
        )

        val pages = extractor.pages(ExtractionWindow(null, null), pageSize = 1, maxPages = 3).toList()

        assertThat(pages).hasSize(3)
    }

    @Test
    fun `transient errors are retried and succeed`() {
        wireMock.stubFor(
            get(urlPathEqualTo("/api/customers"))
                .inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"),
        )
        wireMock.stubFor(
            get(urlPathEqualTo("/api/customers"))
                .inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson("""{"items":[],"nextCursor":null,"hasMore":false}""")),
        )

        val page = extractor.fetchChanges(ExtractionWindow(null, null), null, 10)

        assertThat(page.records).isEmpty()
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/api/customers")))
    }

    @Test
    fun `persistent outage surfaces as SourceUnavailableException after the retries`() {
        wireMock.stubFor(get(urlPathEqualTo("/api/customers")).willReturn(aResponse().withStatus(502)))

        assertThrows<SourceUnavailableException> { extractor.fetchChanges(ExtractionWindow(null, null), null, 10) }

        wireMock.verify(3, getRequestedFor(urlPathEqualTo("/api/customers")))
    }

    @Test
    fun `rejected credentials are not retried`() {
        wireMock.stubFor(get(urlPathEqualTo("/api/customers")).willReturn(aResponse().withStatus(401)))

        assertThrows<SourceAuthException> { extractor.fetchChanges(ExtractionWindow(null, null), null, 10) }

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/customers")))
    }

    @Test
    fun `a record the hub cannot track is a response error, not silently skipped`() {
        wireMock.stubFor(
            get(urlPathEqualTo("/api/customers"))
                .willReturn(okJson("""{"items":[{"email":"no-id@example.com"}],"nextCursor":null,"hasMore":false}""")),
        )

        val ex =
            assertThrows<SourceResponseException> { extractor.fetchChanges(ExtractionWindow(null, null), null, 10) }

        assertThat(ex.message).contains("without id/updatedAt")
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/customers")))
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
