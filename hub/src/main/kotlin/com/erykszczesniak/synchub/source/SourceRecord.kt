package com.erykszczesniak.synchub.source

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/**
 * One raw record as the source sent it. The three fields the hub needs before it understands the
 * payload (key, version clock, tombstone flag) are lifted out; the rest stays as JSON so schema-drift
 * detection can look at exactly what arrived.
 */
data class SourceRecord(
    val feed: String,
    val businessKey: String,
    val sourceUpdatedAt: Instant,
    val deleted: Boolean,
    val payload: JsonNode,
)

data class SourcePage(
    val records: List<SourceRecord>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

/** Extraction bounds on the source's `updatedAt`: exclusive [since], inclusive [until]. */
data class ExtractionWindow(
    val since: Instant?,
    val until: Instant?,
)
