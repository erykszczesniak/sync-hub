package com.erykszczesniak.systema.api

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import java.time.Instant

/** Query parameters of the change feed; bound from the query string and documented in one place. */
data class ChangesQuery(
    @field:Schema(description = "Exclusive lower bound on updatedAt (ISO-8601)")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val updatedSince: Instant? = null,
    @field:Schema(description = "Inclusive upper bound on updatedAt (ISO-8601)")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val updatedUntil: Instant? = null,
    @field:Schema(description = "Opaque cursor from the previous page's nextCursor")
    val cursor: String? = null,
    @field:Schema(description = "Page size, 1..500")
    @field:Min(1)
    @field:Max(500)
    val limit: Int = 100,
)
