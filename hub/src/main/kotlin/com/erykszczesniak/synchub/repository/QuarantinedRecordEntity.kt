package com.erykszczesniak.synchub.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class QuarantineReason { DRIFT, VALIDATION, MAPPING }

enum class QuarantineStatus { OPEN, REPLAYED, SUPERSEDED, DISCARDED }

/**
 * A source record the hub refused to load. The raw payload is kept verbatim so the record can be
 * replayed once the cause (schema drift, invalid data) is fixed; nothing is silently dropped.
 */
@Entity
@Table(name = "quarantined_record")
class QuarantinedRecordEntity(
    @Id
    @Column(name = "id")
    val id: UUID = UUID.randomUUID(),
    @Column(name = "feed", nullable = false, length = 64)
    val feed: String,
    @Column(name = "business_key", nullable = false, length = 128)
    val businessKey: String,
    @Column(name = "run_id", nullable = false)
    val runId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 16)
    val reason: QuarantineReason,
    @Column(name = "details", nullable = false, length = 2000)
    val details: String,
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,
    @Column(name = "source_updated_at")
    val sourceUpdatedAt: Instant? = null,
    @Column(name = "quarantined_at", nullable = false)
    val quarantinedAt: Instant = Instant.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: QuarantineStatus = QuarantineStatus.OPEN,
    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
    @Column(name = "resolution_note", length = 500)
    var resolutionNote: String? = null,
)
