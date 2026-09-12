package com.erykszczesniak.synchub.source.graphql

import com.erykszczesniak.synchub.common.SourceResponseException
import com.erykszczesniak.synchub.source.ExtractionWindow
import com.erykszczesniak.synchub.source.SourceExtractor
import com.erykszczesniak.synchub.source.SourcePage
import com.erykszczesniak.synchub.source.SourceRecord
import com.erykszczesniak.synchub.source.SystemAClientConfig.Companion.RESILIENCE_INSTANCE
import com.erykszczesniak.synchub.source.SystemAErrors
import com.erykszczesniak.synchub.source.SystemAProperties
import com.fasterxml.jackson.databind.JsonNode
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.graphql.client.GraphQlTransportException
import org.springframework.graphql.client.HttpGraphQlClient
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * GraphQL extractor for System A's orders. Same contract and the same resilience policy as the REST
 * extractor; only the transport differs: one POST per page with a Relay-style connection query,
 * `after` cursor and `first` page size.
 *
 * GraphQL reports application errors in the `errors` array with HTTP 200, so a response with errors
 * is treated as malformed (not retried) while transport failures follow the shared translation.
 */
@Component
class OrderGraphQlExtractor(
    @Qualifier("systemAWebClient") webClient: WebClient,
    properties: SystemAProperties,
) : SourceExtractor {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: HttpGraphQlClient =
        HttpGraphQlClient
            .builder(webClient.mutate().baseUrl(properties.baseUrl.trimEnd('/') + "/graphql").build())
            .build()

    override val feed: String = FEED

    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @RateLimiter(name = RESILIENCE_INSTANCE)
    override fun fetchChanges(
        window: ExtractionWindow,
        cursor: String?,
        pageSize: Int,
    ): SourcePage {
        val response =
            try {
                client
                    .document(ORDERS_QUERY)
                    .variable("updatedSince", window.since?.toString())
                    .variable("updatedUntil", window.until?.toString())
                    .variable("after", cursor)
                    .variable("first", pageSize)
                    .execute()
                    .block()
            } catch (ex: WebClientResponseException) {
                throw SystemAErrors.translate(ex)
            } catch (ex: WebClientRequestException) {
                throw SystemAErrors.translate(ex)
            } catch (ex: GraphQlTransportException) {
                throw SystemAErrors.translate(ex)
            } ?: throw SourceResponseException("System A returned no GraphQL response")

        if (!response.isValid) {
            throw SourceResponseException(
                "System A GraphQL errors: " + response.errors.joinToString { it.message ?: "?" },
            )
        }
        val connection =
            response.field("orders").toEntity(JsonNode::class.java)
                ?: throw SourceResponseException("System A GraphQL response has no 'orders' field")
        val edges = connection.path("edges")
        if (!edges.isArray) throw SourceResponseException("System A GraphQL response has no 'orders.edges'")
        val records = edges.map { toRecord(it.path("node")) }
        val pageInfo = connection.path("pageInfo")
        log.debug(
            "Fetched {} order changes (after={}, hasNextPage={})",
            records.size,
            cursor,
            pageInfo.path("hasNextPage").asBoolean(),
        )
        return SourcePage(
            records = records,
            nextCursor = pageInfo.path("endCursor").takeIf { it.isTextual }?.asText(),
            hasMore = pageInfo.path("hasNextPage").asBoolean(false),
        )
    }

    private fun toRecord(node: JsonNode): SourceRecord {
        val id = node.path("id").takeIf { it.isTextual }?.asText()
        val updatedAt =
            try {
                node.path("updatedAt").takeIf { it.isTextual }?.let { Instant.parse(it.asText()) }
            } catch (ex: DateTimeParseException) {
                throw SourceResponseException("Order ${id ?: "?"} has an unparsable updatedAt", ex)
            }
        if (id.isNullOrBlank() || updatedAt == null) {
            throw SourceResponseException("Order record without id/updatedAt cannot be tracked: $node")
        }
        return SourceRecord(FEED, id, updatedAt, node.path("deleted").asBoolean(false), node)
    }

    companion object {
        const val FEED = "orders"

        val ORDERS_QUERY: String =
            """
            query OrderChanges(${'$'}updatedSince: String, ${'$'}updatedUntil: String, ${'$'}after: String, ${'$'}first: Int) {
              orders(updatedSince: ${'$'}updatedSince, updatedUntil: ${'$'}updatedUntil, after: ${'$'}after, first: ${'$'}first) {
                edges {
                  node {
                    id customerId status
                    total { amount currency }
                    lines { sku quantity unitPrice }
                    placedAt updatedAt deleted
                  }
                }
                pageInfo { endCursor hasNextPage }
              }
            }
            """.trimIndent()
    }
}
