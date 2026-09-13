package com.erykszczesniak.systema.api

import com.erykszczesniak.systema.drift.DriftMutator
import com.erykszczesniak.systema.store.SystemAStore
import com.fasterxml.jackson.databind.node.ObjectNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class CustomerChangesResponse(
    val items: List<ObjectNode>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

@RestController
@RequestMapping("/api/customers")
@Validated
@Tag(name = "Customers (REST)", description = "Change feed of customers ordered by updatedAt; requires X-API-Key")
class CustomersController(
    private val store: SystemAStore,
    private val drift: DriftMutator,
) {
    @GetMapping
    @Operation(
        summary = "List customers changed in a window",
        description =
            "Returns customers with updatedAt > updatedSince (and <= updatedUntil when given), ordered by " +
                "(updatedAt, id). Deleted customers are included as tombstones (deleted=true). Page with nextCursor.",
    )
    fun changes(
        @Parameter(description = "Exclusive lower bound on updatedAt (ISO-8601)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        updatedSince: Instant?,
        @Parameter(description = "Inclusive upper bound on updatedAt (ISO-8601)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        updatedUntil: Instant?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "100") @Min(1) @Max(500) limit: Int,
    ): CustomerChangesResponse {
        val page = store.customers.changes(updatedSince, updatedUntil, cursor, limit)
        return CustomerChangesResponse(page.items.map(drift::customer), page.nextCursor, page.hasMore)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one customer by id")
    fun byId(
        @PathVariable id: String,
    ): ResponseEntity<ObjectNode> =
        store.customers.get(id)?.let { ResponseEntity.ok(drift.customer(it)) } ?: ResponseEntity.notFound().build()
}
