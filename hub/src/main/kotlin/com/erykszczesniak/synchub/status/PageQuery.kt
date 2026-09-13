package com.erykszczesniak.synchub.status

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** Paging parameters shared by every list endpoint. */
data class PageQuery(
    @field:Schema(description = "Zero-based page number")
    @field:Min(0)
    val page: Int = 0,
    @field:Schema(description = "Page size, 1..200")
    @field:Min(1)
    @field:Max(200)
    val size: Int = 20,
)
