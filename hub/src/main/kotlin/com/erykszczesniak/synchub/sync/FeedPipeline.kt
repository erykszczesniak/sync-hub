package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.canonical.CanonicalChange
import com.erykszczesniak.synchub.events.ChangeEventRecorder
import com.erykszczesniak.synchub.sink.LoadResult
import com.erykszczesniak.synchub.sink.SinkLoader
import com.erykszczesniak.synchub.sink.SinkMapper
import com.erykszczesniak.synchub.sink.SystemBRecord
import com.erykszczesniak.synchub.source.SourceExtractor
import com.erykszczesniak.synchub.source.SourceRecord
import com.erykszczesniak.synchub.transform.SchemaContract
import com.erykszczesniak.synchub.transform.SourceMapper
import java.time.Instant
import java.util.UUID

/**
 * Everything the orchestrator needs to know about one feed, wired once per (source, destination)
 * pair. The generics keep the canonical type in the middle: the extractor knows nothing about System
 * B and the loader knows nothing about System A.
 */
class FeedPipeline<C : Any, B : SystemBRecord>(
    val name: String,
    val protocol: String,
    val extractor: SourceExtractor,
    val contract: SchemaContract,
    private val sourceMapper: SourceMapper<C>,
    private val sinkMapper: SinkMapper<C, B>,
    private val loader: SinkLoader<B>,
) {
    fun toCanonical(record: SourceRecord): CanonicalChange<C> = sourceMapper.toCanonical(record)

    /** Map to System B, merge idempotently and, if anything changed, record the change event (same transaction). */
    fun load(
        change: CanonicalChange<C>,
        runId: UUID,
        recorder: ChangeEventRecorder,
        now: Instant,
    ): LoadResult<B> {
        val record = change.record?.let(sinkMapper::toSystemB)
        val result = loader.load(change.businessKey, change.sourceUpdatedAt, record, now)
        if (result.applied) recorder.record(name, runId, result, now)
        return result
    }
}
