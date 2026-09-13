package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.repository.DriftEventEntity
import com.erykszczesniak.synchub.repository.DriftEventRepository
import com.erykszczesniak.synchub.repository.DriftStatus
import com.erykszczesniak.synchub.transform.DriftFinding
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import com.erykszczesniak.synchub.repository.DriftKind as StoredDriftKind

/**
 * Turns per-record drift findings into per-feed drift events. One open event per distinct finding:
 * repeated detections increase `affectedRecords` and `lastSeenAt` instead of flooding the operator.
 */
@Service
class DriftEventService(
    private val repository: DriftEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun record(
        feed: String,
        runId: UUID,
        findings: List<DriftFinding>,
        sourceUpdatedAt: Instant?,
        now: Instant = Instant.now(),
    ): List<DriftEventEntity> =
        findings.map { finding ->
            val existing = repository.findByFeedAndFingerprintAndStatus(feed, finding.fingerprint, DriftStatus.OPEN)
            if (existing != null) {
                existing.affectedRecords += 1
                existing.lastRunId = runId
                existing.lastSeenAt = now
                existing.sourceChangedAt = earliest(existing.sourceChangedAt, sourceUpdatedAt)
                repository.save(existing)
            } else {
                log.warn("Schema drift detected on feed '{}': {}", feed, finding.details)
                repository.save(
                    DriftEventEntity(
                        feed = feed,
                        fingerprint = finding.fingerprint,
                        kind = StoredDriftKind.valueOf(finding.kind.name),
                        field = finding.field,
                        expected = finding.expected,
                        actual = finding.actual,
                        details = finding.details,
                        affectedRecords = 1,
                        firstRunId = runId,
                        lastRunId = runId,
                        sourceChangedAt = sourceUpdatedAt,
                        detectedAt = now,
                        lastSeenAt = now,
                    ),
                )
            }
        }

    @Transactional
    fun resolve(
        id: UUID,
        note: String?,
        now: Instant = Instant.now(),
    ): DriftEventEntity? {
        val event = repository.findById(id).orElse(null) ?: return null
        if (event.status == DriftStatus.OPEN) {
            event.status = DriftStatus.RESOLVED
            event.resolvedAt = now
            event.resolutionNote = note
            repository.save(event)
            log.info("Drift event {} on feed '{}' resolved: {}", id, event.feed, note ?: "no note")
        }
        return event
    }

    /** Used when a clean backfill proves the drift is gone. */
    @Transactional
    fun resolveAllOpen(
        feed: String,
        note: String,
        now: Instant = Instant.now(),
    ): Int = repository.findByFeedAndStatus(feed, DriftStatus.OPEN).onEach { resolve(it.id, note, now) }.size

    private fun earliest(
        a: Instant?,
        b: Instant?,
    ): Instant? = listOfNotNull(a, b).minOrNull()
}
