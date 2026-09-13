package com.erykszczesniak.synchub.source.rest

import com.erykszczesniak.synchub.common.SourceResponseException
import com.erykszczesniak.synchub.source.ExtractionWindow
import com.erykszczesniak.synchub.source.SourceExtractor
import com.erykszczesniak.synchub.source.SourcePage
import com.erykszczesniak.synchub.source.SourceRecord
import com.erykszczesniak.synchub.source.SystemAClientConfig.Companion.RESILIENCE_INSTANCE
import com.erykszczesniak.synchub.source.SystemAErrors
import com.fasterxml.jackson.databind.JsonNode
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * REST extractor for System A's customer change feed.
 *
 * Resilience (resilience4j, configured under `resilience4j.*` in application.yml):
 * - `@Retry`: transient failures (5xx, timeouts, connection errors) are retried with backoff; auth
 *   and malformed responses are not, because a retry cannot fix them.
 * - `@RateLimiter`: the hub never calls the source faster than its documented quota.
 * - `@CircuitBreaker`: after repeated failures the hub stops hammering a sick source and fails fast;
 *   the run is marked FAILED, the watermark stays put and the next scheduled run tries again.
 */
@Component
class CustomerRestExtractor(
    @Qualifier("systemAWebClient") private val webClient: WebClient,
) : SourceExtractor {
    private val log = LoggerFactory.getLogger(javaClass)

    override val feed: String = FEED

    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @RateLimiter(name = RESILIENCE_INSTANCE)
    override fun fetchChanges(
        window: ExtractionWindow,
        cursor: String?,
        pageSize: Int,
    ): SourcePage {
        val body =
            try {
                webClient
                    .get()
                    .uri { uri ->
                        uri.path("/api/customers").queryParam("limit", pageSize)
                        window.since?.let { uri.queryParam("updatedSince", it.toString()) }
                        window.until?.let { uri.queryParam("updatedUntil", it.toString()) }
                        cursor?.let { uri.queryParam("cursor", it) }
                        uri.build()
                    }.retrieve()
                    .onStatus(HttpStatusCode::isError) { response -> response.createException() }
                    .bodyToMono(JsonNode::class.java)
                    .block()
            } catch (ex: WebClientResponseException) {
                throw SystemAErrors.translate(ex)
            } catch (ex: WebClientRequestException) {
                throw SystemAErrors.translate(ex)
            } ?: throw SourceResponseException("System A returned an empty body for /api/customers")

        val items = body.path("items")
        if (!items.isArray) throw SourceResponseException("System A response has no 'items' array")
        val records = items.map(::toRecord)
        log.debug(
            "Fetched {} customer changes (cursor={}, hasMore={})",
            records.size,
            cursor,
            body.path("hasMore").asBoolean(),
        )
        return SourcePage(
            records = records,
            nextCursor = body.path("nextCursor").takeIf { it.isTextual }?.asText(),
            hasMore = body.path("hasMore").asBoolean(false),
        )
    }

    private fun toRecord(node: JsonNode): SourceRecord {
        val id = node.path("id").takeIf { it.isTextual }?.asText()
        val updatedAt =
            try {
                node.path("updatedAt").takeIf { it.isTextual }?.let { Instant.parse(it.asText()) }
            } catch (ex: DateTimeParseException) {
                throw SourceResponseException("Customer ${id ?: "?"} has an unparsable updatedAt", ex)
            }
        if (id.isNullOrBlank() || updatedAt == null) {
            throw SourceResponseException("Customer record without id/updatedAt cannot be tracked: $node")
        }
        return SourceRecord(
            feed = FEED,
            businessKey = id,
            sourceUpdatedAt = updatedAt,
            deleted = node.path("deleted").asBoolean(false),
            payload = node,
        )
    }

    companion object {
        const val FEED = "customers"
    }
}
