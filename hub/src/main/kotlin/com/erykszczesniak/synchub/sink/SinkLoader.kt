package com.erykszczesniak.synchub.sink

import com.erykszczesniak.synchub.canonical.ChangeType
import com.erykszczesniak.synchub.common.Fingerprints
import com.erykszczesniak.synchub.repository.SystemBEntity
import org.slf4j.LoggerFactory
import java.time.Instant

enum class LoadOutcome(
    /** The change this outcome represents for downstream consumers, or null when nothing changed. */
    val changeType: ChangeType?,
) {
    CREATED(ChangeType.CREATED),
    UPDATED(ChangeType.UPDATED),
    DELETED(ChangeType.DELETED),

    /** Same business content already in System B (replay, backfill, overlap re-read). */
    SKIPPED_UNCHANGED(null),

    /** System B already holds a newer version; the incoming record arrived late or out of order. */
    SKIPPED_STALE(null),
}

data class LoadResult<B : SystemBRecord>(
    val businessKey: String,
    val outcome: LoadOutcome,
    val before: B?,
    val after: B?,
    val version: Int,
    val sourceUpdatedAt: Instant,
) {
    val applied: Boolean get() = outcome.changeType != null
}

/** Idempotent merge of one canonical change into System B. `record == null` is a delete. */
interface SinkLoader<B : SystemBRecord> {
    val feed: String

    fun load(
        businessKey: String,
        sourceUpdatedAt: Instant,
        record: B?,
        now: Instant = Instant.now(),
    ): LoadResult<B>
}

/**
 * The merge rules every feed shares. They are what make replays, backfills and out-of-order delivery
 * safe:
 *
 * 1. Rows are keyed on the stable business key; there is never a second row for the same key.
 * 2. A version older than the one stored (`sourceUpdatedAt` strictly before) is SKIPPED_STALE:
 *    late-arriving records can never overwrite newer state. Equal timestamps apply (last writer wins).
 * 3. Same content (fingerprint) is SKIPPED_UNCHANGED, with the version clock nudged forward silently,
 *    so replaying a window produces no writes and no change events.
 * 4. Deletes are soft (`deletedAt`) so a later resurrection or a late update is still decidable.
 */
abstract class AbstractSinkLoader<B : SystemBRecord, E : SystemBEntity> : SinkLoader<B> {
    private val log = LoggerFactory.getLogger(javaClass)

    protected abstract fun find(businessKey: String): E?

    protected abstract fun create(
        record: B,
        fingerprint: String,
        now: Instant,
    ): E

    protected abstract fun apply(
        entity: E,
        record: B,
    )

    protected abstract fun toRecord(entity: E): B

    protected abstract fun save(entity: E): E

    override fun load(
        businessKey: String,
        sourceUpdatedAt: Instant,
        record: B?,
        now: Instant,
    ): LoadResult<B> {
        val existing = find(businessKey)
        val result =
            if (record ==
                null
            ) {
                delete(businessKey, sourceUpdatedAt, existing, now)
            } else {
                upsert(record, existing, now)
            }
        log.debug("{} '{}' -> {} (v{})", feed, businessKey, result.outcome, result.version)
        return result
    }

    private fun upsert(
        record: B,
        existing: E?,
        now: Instant,
    ): LoadResult<B> {
        val fingerprint = Fingerprints.sha256(record.contentFingerprint())
        if (existing == null) {
            val created = save(create(record, fingerprint, now))
            return LoadResult(
                record.key,
                LoadOutcome.CREATED,
                null,
                toRecord(created),
                created.version,
                created.sourceUpdatedAt,
            )
        }
        if (record.sourceUpdatedAt.isBefore(existing.sourceUpdatedAt)) {
            return LoadResult(
                record.key,
                LoadOutcome.SKIPPED_STALE,
                toRecord(existing),
                null,
                existing.version,
                existing.sourceUpdatedAt,
            )
        }
        val wasDeleted = existing.deletedAt != null
        if (!wasDeleted && existing.contentFingerprint == fingerprint) {
            existing.sourceUpdatedAt = record.sourceUpdatedAt
            existing.lastSyncedAt = now
            save(existing)
            return LoadResult(
                record.key,
                LoadOutcome.SKIPPED_UNCHANGED,
                toRecord(existing),
                null,
                existing.version,
                existing.sourceUpdatedAt,
            )
        }
        val before = if (wasDeleted) null else toRecord(existing)
        apply(existing, record)
        existing.sourceUpdatedAt = record.sourceUpdatedAt
        existing.contentFingerprint = fingerprint
        existing.deletedAt = null
        existing.version += 1
        existing.lastSyncedAt = now
        val saved = save(existing)
        val outcome = if (wasDeleted) LoadOutcome.CREATED else LoadOutcome.UPDATED
        return LoadResult(record.key, outcome, before, toRecord(saved), saved.version, saved.sourceUpdatedAt)
    }

    private fun delete(
        businessKey: String,
        sourceUpdatedAt: Instant,
        existing: E?,
        now: Instant,
    ): LoadResult<B> {
        if (existing == null) {
            return LoadResult(businessKey, LoadOutcome.SKIPPED_UNCHANGED, null, null, 0, sourceUpdatedAt)
        }
        if (sourceUpdatedAt.isBefore(existing.sourceUpdatedAt)) {
            return LoadResult(
                businessKey,
                LoadOutcome.SKIPPED_STALE,
                toRecord(existing),
                null,
                existing.version,
                existing.sourceUpdatedAt,
            )
        }
        if (existing.deletedAt != null) {
            existing.sourceUpdatedAt = sourceUpdatedAt
            existing.lastSyncedAt = now
            save(existing)
            return LoadResult(
                businessKey,
                LoadOutcome.SKIPPED_UNCHANGED,
                null,
                null,
                existing.version,
                existing.sourceUpdatedAt,
            )
        }
        val before = toRecord(existing)
        existing.deletedAt = now
        existing.sourceUpdatedAt = sourceUpdatedAt
        existing.version += 1
        existing.lastSyncedAt = now
        val saved = save(existing)
        return LoadResult(businessKey, LoadOutcome.DELETED, before, null, saved.version, saved.sourceUpdatedAt)
    }
}
