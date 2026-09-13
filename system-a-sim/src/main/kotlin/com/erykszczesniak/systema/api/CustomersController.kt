package com.erykszczesniak.systema.api

import com.erykszczesniak.systema.drift.DriftMutator
import com.erykszczesniak.systema.store.SystemAStore
import com.fasterxml.jackson.databind.node.ObjectNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers (REST)", description = "Change feed of customers ordered by updatedAt; requires X-API-Key")
class CustomersController(
    private val store: SystemAStore,
    private val drift: DriftMutator,
) {
    @GetMapping
    @Operation(
        summary = "List customers changed in a window",
        description =
            "Customers with updatedAt > updatedSince (and <= updatedUntil when given), ordered by (updatedAt, id). " +
                "Deleted customers are included as tombstones (deleted=true). Page with nextCursor.",
    )
    fun changes(
        @ParameterObject @Valid query: ChangesQuery,
    ): CustomerChangesResponse {
        val page = store.customers.changes(query.updatedSince, query.updatedUntil, query.cursor, query.limit)
        return CustomerChangesResponse(page.items.map(drift::customer), page.nextCursor, page.hasMore)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one customer by id")
    fun byId(
        @PathVariable id: String,
    ): ResponseEntity<ObjectNode> =
        store.customers.get(id)?.let { ResponseEntity.ok(drift.customer(it)) } ?: ResponseEntity.notFound().build()
}
