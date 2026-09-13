package com.erykszczesniak.synchub.events

import com.erykszczesniak.synchub.canonical.ChangeType
import java.time.Instant
import java.util.UUID

/**
 * The CDC-style event published for every applied change in System B.
 *
 * `eventId` is the deduplication key for consumers (delivery is at-least-once). `version` is
 * System B's per-row counter, so a consumer can also apply "only if newer" semantics. `before` and
 * `after` are System B records (the destination shape), null when the row did not exist / no longer
 * exists.
 */
data class ChangeEvent(
    val eventId: UUID,
    val feed: String,
    val type: ChangeType,
    val businessKey: String,
    val version: Int,
    val occurredAt: Instant,
    val sourceUpdatedAt: Instant,
    val runId: UUID,
    val before: Any?,
    val after: Any?,
)
