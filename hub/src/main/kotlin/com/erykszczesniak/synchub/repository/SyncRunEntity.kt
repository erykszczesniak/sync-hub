package com.erykszczesniak.synchub.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class SyncMode { INCREMENTAL, BACKFILL }

enum class SyncTrigger { SCHEDULED, MANUAL }

enum class SyncRunStatus { RUNNING, SUCCEEDED, PARTIAL, FAILED }

/** One execution of a feed sync with everything an operator needs to explain it afterwards. */
@Entity
@Table(name = "sync_run")
class SyncRunEntity(
    @Id
    @Column(name = "id")
    val id: UUID = UUID.randomUUID(),
    @Column(name = "feed", nullable = false, length = 64)
    val feed: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    val mode: SyncMode,
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 16)
    val trigger: SyncTrigger,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: SyncRunStatus = SyncRunStatus.RUNNING,
    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),
    @Column(name = "finished_at")
    var finishedAt: Instant? = null,
    @Column(name = "extracted", nullable = false)
    var extracted: Int = 0,
    @Column(name = "transformed", nullable = false)
    var transformed: Int = 0,
    @Column(name = "loaded", nullable = false)
    var loaded: Int = 0,
    @Column(name = "skipped", nullable = false)
    var skipped: Int = 0,
    @Column(name = "quarantined", nullable = false)
    var quarantined: Int = 0,
    @Column(name = "failed", nullable = false)
    var failed: Int = 0,
    @Column(name = "drift_detected", nullable = false)
    var driftDetected: Boolean = false,
    @Column(name = "watermark_before")
    var watermarkBefore: Instant? = null,
    @Column(name = "watermark_after")
    var watermarkAfter: Instant? = null,
    @Column(name = "backfill_from")
    val backfillFrom: Instant? = null,
    @Column(name = "backfill_to")
    val backfillTo: Instant? = null,
    @Column(name = "max_lag_seconds")
    var maxLagSeconds: Long? = null,
    @Column(name = "error_message", length = 2000)
    var errorMessage: String? = null,
)
