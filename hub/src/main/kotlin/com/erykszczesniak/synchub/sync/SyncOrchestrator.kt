package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.common.SourceException
import com.erykszczesniak.synchub.events.OutboxPublisher
import com.erykszczesniak.synchub.repository.SyncMode
import com.erykszczesniak.synchub.repository.SyncRunEntity
import com.erykszczesniak.synchub.repository.SyncRunRepository
import com.erykszczesniak.synchub.repository.SyncRunStatus
import com.erykszczesniak.synchub.repository.SyncTrigger
import com.erykszczesniak.synchub.source.ExtractionWindow
import com.erykszczesniak.synchub.source.SourceRecord
import com.erykszczesniak.synchub.source.SystemAProperties
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

class FeedBusyException(
    feed: String,
) : RuntimeException("A sync of feed '$feed' is already running")

/**
 * Runs one feed end to end and writes the run log.
 *
 * Watermark policy: an incremental run moves the watermark to the newest `updatedAt` it extracted,
 * including quarantined records (they are kept and replayable, not lost), and only if the run did not
 * fail. A failed run leaves the watermark alone, so the next run re-reads the same window; loads are
 * idempotent, so that costs nothing but time. Backfills never move the watermark.
 *
 * Status: SUCCEEDED when every record was loaded or skipped; PARTIAL when some were quarantined;
 * FAILED when the source could not be read (nothing after the failure was processed).
 */
@Service
class SyncOrchestrator(
    private val registry: FeedRegistry,
    private val processor: RecordProcessor,
    private val watermarks: WatermarkStore,
    private val runs: SyncRunRepository,
    private val driftEvents: DriftEventService,
    private val outboxPublisher: OutboxPublisher,
    private val sourceProperties: SystemAProperties,
    private val syncProperties: SyncProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun runIncremental(
        feed: String,
        trigger: SyncTrigger,
    ): SyncRunEntity = run(feed, SyncMode.INCREMENTAL, trigger, window = null)

    fun runBackfill(
        feed: String,
        from: Instant,
        to: Instant,
        trigger: SyncTrigger = SyncTrigger.MANUAL,
    ): SyncRunEntity {
        require(!to.isBefore(from)) { "backfill window end must not be before its start" }
        return run(feed, SyncMode.BACKFILL, trigger, ExtractionWindow(from, to))
    }

    private fun run(
        feed: String,
        mode: SyncMode,
        trigger: SyncTrigger,
        window: ExtractionWindow?,
    ): SyncRunEntity {
        val pipeline = registry.get(feed)
        val lock = locks.computeIfAbsent(feed) { ReentrantLock() }
        if (!lock.tryLock()) throw FeedBusyException(feed)
        try {
            val run =
                runs.save(
                    SyncRunEntity(
                        feed = feed,
                        mode = mode,
                        trigger = trigger,
                        backfillFrom = window?.since,
                        backfillTo = window?.until,
                        watermarkBefore = watermarks.read(feed)?.timestamp,
                    ),
                )
            MDC.put("runId", run.id.toString())
            MDC.put("feed", feed)
            try {
                execute(pipeline, run, window ?: watermarks.incrementalWindow(feed, syncProperties.watermarkOverlap))
            } finally {
                MDC.remove("runId")
                MDC.remove("feed")
            }
            outboxPublisher.publishPending()
            return run
        } finally {
            lock.unlock()
        }
    }

    private fun execute(
        pipeline: FeedPipeline<*, *>,
        run: SyncRunEntity,
        window: ExtractionWindow,
    ) {
        log.info(
            "Sync {} started: mode={}, trigger={}, window=[{} .. {}]",
            run.id,
            run.mode,
            run.trigger,
            window.since,
            window.until,
        )
        val state = RunState()
        try {
            pipeline.extractor
                .pages(window, sourceProperties.pageSize, sourceProperties.maxPagesPerRun)
                .forEach { page -> page.records.forEach { record -> processRecord(pipeline, record, run, state) } }
            finish(run, state, failure = null)
        } catch (ex: SourceException) {
            log.error("Sync {} failed while reading the source: {}", run.id, ex.message)
            finish(run, state, failure = ex)
        }
    }

    /** Dedup inside a run: the same (key, version) twice is processed once; the newest version wins anyway. */
    private fun processRecord(
        pipeline: FeedPipeline<*, *>,
        record: SourceRecord,
        run: SyncRunEntity,
        state: RunState,
    ) {
        state.extracted++
        state.newest = newer(state.newest, Watermark(record.sourceUpdatedAt, record.businessKey))
        if (state.seen[record.businessKey] == record.sourceUpdatedAt) {
            state.skipped++
            return
        }
        state.seen[record.businessKey] = record.sourceUpdatedAt
        processOne(pipeline, record, run, state)
    }

    private fun processOne(
        pipeline: FeedPipeline<*, *>,
        record: SourceRecord,
        run: SyncRunEntity,
        counters: RunState,
    ) {
        val now = Instant.now()
        val result = processor.process(pipeline, record, run.id, now)
        if (result.driftDetected) counters.drift = true
        when (result.outcome) {
            ProcessOutcome.LOADED -> {
                counters.transformed++
                counters.loaded++
                counters.maxLagSeconds =
                    maxOf(counters.maxLagSeconds, Duration.between(record.sourceUpdatedAt, now).seconds)
            }
            ProcessOutcome.SKIPPED -> {
                counters.transformed++
                counters.skipped++
            }
            ProcessOutcome.QUARANTINED -> counters.quarantined++
        }
    }

    private fun finish(
        run: SyncRunEntity,
        counters: RunState,
        failure: Exception?,
    ) {
        val newest = counters.newest
        run.extracted = counters.extracted
        run.transformed = counters.transformed
        run.loaded = counters.loaded
        run.skipped = counters.skipped
        run.quarantined = counters.quarantined
        run.failed = counters.failed
        run.driftDetected = counters.drift
        run.maxLagSeconds = if (counters.loaded > 0) counters.maxLagSeconds else null
        run.finishedAt = Instant.now()
        when {
            failure != null -> {
                run.status = SyncRunStatus.FAILED
                run.errorMessage = failure.message?.take(MAX_ERROR)
            }
            counters.quarantined > 0 -> run.status = SyncRunStatus.PARTIAL
            else -> run.status = SyncRunStatus.SUCCEEDED
        }
        if (run.mode == SyncMode.INCREMENTAL && failure == null && newest != null) {
            run.watermarkAfter = watermarks.advance(run.feed, newest).timestamp
        } else {
            run.watermarkAfter = watermarks.read(run.feed)?.timestamp
        }
        val cleanBackfill = run.mode == SyncMode.BACKFILL && run.status == SyncRunStatus.SUCCEEDED
        if (cleanBackfill && !counters.drift && counters.extracted > 0) {
            val resolved = driftEvents.resolveAllOpen(run.feed, "resolved by clean backfill run ${run.id}")
            if (resolved >
                0
            ) {
                log.info("Clean backfill {} resolved {} open drift event(s) on '{}'", run.id, resolved, run.feed)
            }
        }
        runs.save(run)
        log.info(
            "Sync {} {}: extracted={} loaded={} skipped={} quarantined={} drift={} watermark={} in {} ms",
            run.id,
            run.status,
            run.extracted,
            run.loaded,
            run.skipped,
            run.quarantined,
            run.driftDetected,
            run.watermarkAfter,
            Duration.between(run.startedAt, run.finishedAt).toMillis(),
        )
    }

    private fun newer(
        current: Watermark?,
        candidate: Watermark,
    ): Watermark =
        when {
            current == null -> candidate
            candidate.timestamp.isAfter(current.timestamp) -> candidate
            candidate.timestamp == current.timestamp && (candidate.key ?: "") > (current.key ?: "") -> candidate
            else -> current
        }

    private class RunState {
        val seen = HashMap<String, Instant>()
        var newest: Watermark? = null
        var extracted = 0
        var transformed = 0
        var loaded = 0
        var skipped = 0
        var quarantined = 0
        var failed = 0
        var drift = false
        var maxLagSeconds = 0L
    }

    companion object {
        private const val MAX_ERROR = 2000
    }
}
