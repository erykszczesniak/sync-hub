package com.erykszczesniak.synchub.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class DriftKind { FIELD_ADDED, FIELD_MISSING, FIELD_RETYPED, FIELD_RENAMED, VALUE_OUT_OF_DOMAIN }

enum class DriftStatus { OPEN, RESOLVED }

/**
 * A detected difference between the source payloads and the schema contract the hub expects.
 * One event per distinct finding ([fingerprint]) while it stays open; repeated detections bump
 * [affectedRecords] and [lastSeenAt] instead of creating duplicates.
 *
 * [sourceChangedAt] is the earliest source `updatedAt` among affected records, which makes
 * `detectedAt - sourceChangedAt` the mean-time-to-detect (MTTD) input; `resolvedAt - detectedAt`
 * is the mean-time-to-resolve (MTTR) input.
 */
@Entity
@Table(name = "drift_event")
class DriftEventEntity(
    @Id
    @Column(name = "id")
    val id: UUID = UUID.randomUUID(),
    @Column(name = "feed", nullable = false, length = 64)
    val feed: String,
    @Column(name = "fingerprint", nullable = false, length = 256)
    val fingerprint: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    val kind: DriftKind,
    @Column(name = "field", nullable = false, length = 128)
    val field: String,
    @Column(name = "expected", length = 256)
    val expected: String? = null,
    @Column(name = "actual", length = 256)
    val actual: String? = null,
    @Column(name = "details", nullable = false, length = 1000)
    val details: String,
    @Column(name = "affected_records", nullable = false)
    var affectedRecords: Int = 0,
    @Column(name = "first_run_id", nullable = false)
    val firstRunId: UUID,
    @Column(name = "last_run_id", nullable = false)
    var lastRunId: UUID,
    @Column(name = "source_changed_at")
    var sourceChangedAt: Instant? = null,
    @Column(name = "detected_at", nullable = false)
    val detectedAt: Instant = Instant.now(),
    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: DriftStatus = DriftStatus.OPEN,
    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
    @Column(name = "resolution_note", length = 500)
    var resolutionNote: String? = null,
)
