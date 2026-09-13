package com.erykszczesniak.synchub.status

import com.erykszczesniak.synchub.repository.DriftEventEntity
import com.erykszczesniak.synchub.repository.QuarantinedRecordEntity
import com.erykszczesniak.synchub.repository.SyncRunEntity
import org.springframework.data.domain.Page
import java.time.Instant

enum class FeedHealth { HEALTHY, STALE, DRIFT, FAILING, NEVER_RUN }

data class FeedHealthDto(
    val feed: String,
    val protocol: String,
    val health: FeedHealth,
    val running: Boolean,
    val watermark: Instant?,
    val lastRun: SyncRunDto?,
    val lastSuccessfulRun: SyncRunDto?,
    /** Seconds since the last successful run finished; null when it never succeeded. */
    val freshnessSeconds: Long?,
    /** Max (load time − source updatedAt) seen in the last run that loaded anything. */
    val lagSeconds: Long?,
    val openDriftEvents: Long,
    val openQuarantine: Long,
    val activeRecords: Long,
)

data class QuarantinedRecordDto(
    val id: String,
    val feed: String,
    val businessKey: String,
    val runId: String,
    val reason: String,
    val details: String,
    val payload: String,
    val sourceUpdatedAt: Instant?,
    val quarantinedAt: Instant,
    val status: String,
    val resolvedAt: Instant?,
    val resolutionNote: String?,
)

data class DriftEventDto(
    val id: String,
    val feed: String,
    val kind: String,
    val severity: String,
    val field: String,
    val expected: String?,
    val actual: String?,
    val details: String,
    val affectedRecords: Int,
    val firstRunId: String,
    val lastRunId: String,
    val sourceChangedAt: Instant?,
    val detectedAt: Instant,
    val lastSeenAt: Instant,
    val status: String,
    val resolvedAt: Instant?,
    val resolutionNote: String?,
    /** detectedAt − sourceChangedAt, the time the drift existed before the hub saw it. */
    val timeToDetectSeconds: Long?,
    /** resolvedAt − detectedAt. */
    val timeToResolveSeconds: Long?,
)

data class OverviewDto(
    val generatedAt: Instant,
    val feeds: List<FeedHealthDto>,
    val totals: TotalsDto,
    val mttdSeconds: Long?,
    val mttrSeconds: Long?,
    val outboxPending: Long,
    val outboxPublished: Long,
)

data class TotalsDto(
    val runs: Long,
    val succeeded: Long,
    val partial: Long,
    val failed: Long,
    val loaded: Long,
    val skipped: Long,
    val quarantined: Long,
    val openQuarantine: Long,
    val openDriftEvents: Long,
)

data class PageDto<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

fun <T, R> Page<T>.toDto(mapper: (T) -> R) = PageDto(content.map(mapper), number, size, totalElements, totalPages)

fun QuarantinedRecordEntity.toDto() =
    QuarantinedRecordDto(
        id = id.toString(),
        feed = feed,
        businessKey = businessKey,
        runId = runId.toString(),
        reason = reason.name,
        details = details,
        payload = payload,
        sourceUpdatedAt = sourceUpdatedAt,
        quarantinedAt = quarantinedAt,
        status = status.name,
        resolvedAt = resolvedAt,
        resolutionNote = resolutionNote,
    )

fun DriftEventEntity.toDto() =
    DriftEventDto(
        id = id.toString(),
        feed = feed,
        kind = kind.name,
        severity = if (kind == com.erykszczesniak.synchub.repository.DriftKind.FIELD_ADDED) "WARNING" else "BLOCKING",
        field = field,
        expected = expected,
        actual = actual,
        details = details,
        affectedRecords = affectedRecords,
        firstRunId = firstRunId.toString(),
        lastRunId = lastRunId.toString(),
        sourceChangedAt = sourceChangedAt,
        detectedAt = detectedAt,
        lastSeenAt = lastSeenAt,
        status = status.name,
        resolvedAt = resolvedAt,
        resolutionNote = resolutionNote,
        timeToDetectSeconds =
            sourceChangedAt?.let {
                java.time.Duration
                    .between(
                        it,
                        detectedAt,
                    ).seconds
                    .coerceAtLeast(0)
            },
        timeToResolveSeconds =
            resolvedAt?.let {
                java.time.Duration
                    .between(detectedAt, it)
                    .seconds
                    .coerceAtLeast(0)
            },
    )

data class SyncRunDto(
    val id: String,
    val feed: String,
    val mode: String,
    val trigger: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val durationMs: Long?,
    val extracted: Int,
    val transformed: Int,
    val loaded: Int,
    val skipped: Int,
    val quarantined: Int,
    val failed: Int,
    val driftDetected: Boolean,
    val watermarkBefore: Instant?,
    val watermarkAfter: Instant?,
    val backfillFrom: Instant?,
    val backfillTo: Instant?,
    val maxLagSeconds: Long?,
    val errorMessage: String?,
)

fun SyncRunEntity.toDto() =
    SyncRunDto(
        id = id.toString(),
        feed = feed,
        mode = mode.name,
        trigger = trigger.name,
        status = status.name,
        startedAt = startedAt,
        finishedAt = finishedAt,
        durationMs =
            finishedAt?.let {
                java.time.Duration
                    .between(startedAt, it)
                    .toMillis()
            },
        extracted = extracted,
        transformed = transformed,
        loaded = loaded,
        skipped = skipped,
        quarantined = quarantined,
        failed = failed,
        driftDetected = driftDetected,
        watermarkBefore = watermarkBefore,
        watermarkAfter = watermarkAfter,
        backfillFrom = backfillFrom,
        backfillTo = backfillTo,
        maxLagSeconds = maxLagSeconds,
        errorMessage = errorMessage,
    )
