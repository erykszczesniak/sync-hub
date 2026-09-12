package com.erykszczesniak.synchub.status

import com.erykszczesniak.synchub.repository.DriftEventRepository
import com.erykszczesniak.synchub.repository.DriftStatus
import com.erykszczesniak.synchub.repository.OutboxEventRepository
import com.erykszczesniak.synchub.repository.QuarantineStatus
import com.erykszczesniak.synchub.repository.QuarantinedRecordRepository
import com.erykszczesniak.synchub.repository.SyncRunRepository
import com.erykszczesniak.synchub.repository.SyncRunStatus
import com.erykszczesniak.synchub.repository.SystemBCustomerRepository
import com.erykszczesniak.synchub.repository.SystemBOrderRepository
import com.erykszczesniak.synchub.sync.FeedRegistry
import com.erykszczesniak.synchub.sync.WatermarkStore
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ConfigurationProperties(prefix = "synchub.status")
data class StatusProperties(
    /** A feed whose last success is older than this is reported STALE. */
    val staleAfter: Duration = DEFAULT_STALE_AFTER,
) {
    companion object {
        val DEFAULT_STALE_AFTER: Duration = Duration.ofMinutes(5)
    }
}

/** Read-only view of the hub's state for the dashboard. Everything is derived from the run log and hub-state tables. */
@Service
@Transactional(readOnly = true)
class StatusService(
    private val registry: FeedRegistry,
    private val runs: SyncRunRepository,
    private val quarantine: QuarantinedRecordRepository,
    private val drift: DriftEventRepository,
    private val outbox: OutboxEventRepository,
    private val watermarks: WatermarkStore,
    private val customers: SystemBCustomerRepository,
    private val orders: SystemBOrderRepository,
    private val properties: StatusProperties,
) {
    fun feeds(now: Instant = Instant.now()): List<FeedHealthDto> = registry.all().map { feed(it.name, now) }

    fun feed(
        name: String,
        now: Instant = Instant.now(),
    ): FeedHealthDto {
        val pipeline = registry.get(name)
        val lastRun = runs.findFirstByFeedOrderByStartedAtDesc(name)
        val lastSuccess = runs.findFirstByFeedAndStatusOrderByStartedAtDesc(name, SyncRunStatus.SUCCEEDED)
        val lastCompleted = lastRun?.takeIf { it.status != SyncRunStatus.RUNNING }
        val openDrift = drift.countByFeedAndStatus(name, DriftStatus.OPEN)
        val freshness = lastSuccess?.finishedAt?.let { Duration.between(it, now).seconds }
        val health =
            when {
                lastRun == null -> FeedHealth.NEVER_RUN
                lastCompleted?.status == SyncRunStatus.FAILED -> FeedHealth.FAILING
                openDrift > 0 -> FeedHealth.DRIFT
                freshness == null || freshness > properties.staleAfter.seconds -> FeedHealth.STALE
                else -> FeedHealth.HEALTHY
            }
        return FeedHealthDto(
            feed = name,
            protocol = pipeline.protocol,
            health = health,
            running = runs.existsByFeedAndStatus(name, SyncRunStatus.RUNNING),
            watermark = watermarks.read(name)?.timestamp,
            lastRun = lastRun?.toDto(),
            lastSuccessfulRun = lastSuccess?.toDto(),
            freshnessSeconds = freshness,
            lagSeconds = lastCompleted?.maxLagSeconds ?: lastSuccess?.maxLagSeconds,
            openDriftEvents = openDrift,
            openQuarantine = quarantine.countByFeedAndStatus(name, QuarantineStatus.OPEN),
            activeRecords = activeRecords(name),
        )
    }

    fun runs(
        feed: String?,
        page: Int,
        size: Int,
    ): PageDto<SyncRunDto> {
        val pageable = PageRequest.of(page, size)
        val result =
            if (feed ==
                null
            ) {
                runs.findAllByOrderByStartedAtDesc(pageable)
            } else {
                runs.findByFeedOrderByStartedAtDesc(feed, pageable)
            }
        return result.toDto { it.toDto() }
    }

    fun run(id: UUID): SyncRunDto =
        runs.findById(id).orElseThrow { NoSuchElementException("Run $id not found") }.toDto()

    fun quarantine(
        feed: String?,
        status: QuarantineStatus,
        page: Int,
        size: Int,
    ): PageDto<QuarantinedRecordDto> {
        val pageable = PageRequest.of(page, size)
        val result =
            if (feed == null) {
                quarantine.findByStatusOrderByQuarantinedAtDesc(status, pageable)
            } else {
                quarantine.findByFeedAndStatusOrderByQuarantinedAtDesc(feed, status, pageable)
            }
        return result.toDto { it.toDto() }
    }

    fun driftEvents(
        status: DriftStatus?,
        page: Int,
        size: Int,
    ): PageDto<DriftEventDto> {
        val pageable = PageRequest.of(page, size)
        val result =
            if (status ==
                null
            ) {
                drift.findAllByOrderByDetectedAtDesc(pageable)
            } else {
                drift.findByStatusOrderByDetectedAtDesc(status, pageable)
            }
        return result.toDto { it.toDto() }
    }

    fun overview(now: Instant = Instant.now()): OverviewDto {
        val allRuns = runs.findAll()
        val allDrift = drift.findAll()
        val detectTimes =
            allDrift.mapNotNull { e ->
                e.sourceChangedAt?.let { Duration.between(it, e.detectedAt).seconds.coerceAtLeast(0) }
            }
        val resolveTimes =
            allDrift.mapNotNull { e ->
                e.resolvedAt?.let { Duration.between(e.detectedAt, it).seconds.coerceAtLeast(0) }
            }
        return OverviewDto(
            generatedAt = now,
            feeds = feeds(now),
            totals =
                TotalsDto(
                    runs = allRuns.size.toLong(),
                    succeeded = allRuns.count { it.status == SyncRunStatus.SUCCEEDED }.toLong(),
                    partial = allRuns.count { it.status == SyncRunStatus.PARTIAL }.toLong(),
                    failed = allRuns.count { it.status == SyncRunStatus.FAILED }.toLong(),
                    loaded = allRuns.sumOf { it.loaded.toLong() },
                    skipped = allRuns.sumOf { it.skipped.toLong() },
                    quarantined = allRuns.sumOf { it.quarantined.toLong() },
                    openQuarantine =
                        registry.names.sumOf {
                            quarantine.countByFeedAndStatus(
                                it,
                                QuarantineStatus.OPEN,
                            )
                        },
                    openDriftEvents = registry.names.sumOf { drift.countByFeedAndStatus(it, DriftStatus.OPEN) },
                ),
            mttdSeconds = detectTimes.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            mttrSeconds = resolveTimes.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            outboxPending = outbox.countByPublishedAtIsNull(),
            outboxPublished = outbox.countByPublishedAtIsNotNull(),
        )
    }

    private fun activeRecords(feed: String): Long =
        when (feed) {
            "customers" -> customers.countByDeletedAtIsNull()
            "orders" -> orders.countByDeletedAtIsNull()
            else -> 0
        }
}
