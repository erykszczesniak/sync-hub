package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.repository.QuarantineReason
import com.erykszczesniak.synchub.repository.QuarantineStatus
import com.erykszczesniak.synchub.repository.QuarantinedRecordEntity
import com.erykszczesniak.synchub.repository.QuarantinedRecordRepository
import com.erykszczesniak.synchub.source.SourceRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Keeps records the hub refused to load, verbatim, so nothing is lost and everything can be replayed.
 * A record is quarantined at most once per (feed, key) while open: a repeat detection updates the
 * existing row rather than piling up copies.
 */
@Service
class QuarantineService(
    private val repository: QuarantinedRecordRepository,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun quarantine(
        record: SourceRecord,
        runId: UUID,
        reason: QuarantineReason,
        details: String,
        now: Instant = Instant.now(),
    ): QuarantinedRecordEntity {
        val open = repository.findByFeedAndBusinessKeyAndStatus(record.feed, record.businessKey, QuarantineStatus.OPEN)
        // The watermark overlap re-reads recent records on every run; the same version stays one row.
        open.firstOrNull { it.sourceUpdatedAt == record.sourceUpdatedAt }?.let { return it }
        open.forEach {
            it.status = QuarantineStatus.SUPERSEDED
            it.resolvedAt = now
            it.resolutionNote = "superseded by a newer quarantined version"
            repository.save(it)
        }
        log.warn("Quarantined {} record '{}' ({}): {}", record.feed, record.businessKey, reason, details)
        return repository.save(
            QuarantinedRecordEntity(
                feed = record.feed,
                businessKey = record.businessKey,
                runId = runId,
                reason = reason,
                details = details.take(MAX_DETAILS),
                payload = record.payload.toString(),
                sourceUpdatedAt = record.sourceUpdatedAt,
                quarantinedAt = now,
            ),
        )
    }

    /** A successful load of the same key at or after the quarantined version closes the quarantine. */
    @Transactional
    fun supersede(
        feed: String,
        businessKey: String,
        loadedVersion: Instant,
        now: Instant = Instant.now(),
    ): Int =
        repository
            .findByFeedAndBusinessKeyAndStatus(feed, businessKey, QuarantineStatus.OPEN)
            .filter { entity ->
                val quarantinedVersion = entity.sourceUpdatedAt
                quarantinedVersion == null || !quarantinedVersion.isAfter(loadedVersion)
            }.onEach {
                it.status = QuarantineStatus.SUPERSEDED
                it.resolvedAt = now
                it.resolutionNote = "a newer version was loaded successfully"
                repository.save(it)
            }.size

    @Transactional
    fun discard(
        id: UUID,
        note: String?,
        now: Instant = Instant.now(),
    ): QuarantinedRecordEntity? {
        val record = repository.findById(id).orElse(null) ?: return null
        if (record.status == QuarantineStatus.OPEN) {
            record.status = QuarantineStatus.DISCARDED
            record.resolvedAt = now
            record.resolutionNote = note
            repository.save(record)
        }
        return record
    }

    @Transactional
    fun markReplayed(
        id: UUID,
        note: String,
        now: Instant = Instant.now(),
    ) {
        repository.findById(id).ifPresent {
            it.status = QuarantineStatus.REPLAYED
            it.resolvedAt = now
            it.resolutionNote = note
            repository.save(it)
        }
    }

    /** The stored payload of an OPEN quarantined record as a source record, ready to be replayed. */
    @Transactional(readOnly = true)
    fun openForReplay(id: UUID): Pair<QuarantinedRecordEntity, SourceRecord> {
        val entry = repository.findById(id).orElseThrow { NoSuchElementException("Quarantined record $id not found") }
        require(entry.status == QuarantineStatus.OPEN) {
            "Quarantined record $id is ${entry.status}, only OPEN records can be replayed"
        }
        val record =
            SourceRecord(
                entry.feed,
                entry.businessKey,
                entry.sourceUpdatedAt ?: Instant.now(),
                false,
                objectMapper.readTree(entry.payload),
            )
        return entry to record
    }

    companion object {
        private const val MAX_DETAILS = 2000
    }
}
