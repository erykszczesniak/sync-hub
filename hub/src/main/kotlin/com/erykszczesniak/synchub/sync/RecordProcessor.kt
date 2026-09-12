package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.canonical.CanonicalChange
import com.erykszczesniak.synchub.events.ChangeEventRecorder
import com.erykszczesniak.synchub.repository.QuarantineReason
import com.erykszczesniak.synchub.sink.LoadOutcome
import com.erykszczesniak.synchub.sink.SystemBRecord
import com.erykszczesniak.synchub.source.SourceRecord
import com.erykszczesniak.synchub.transform.MappingException
import com.erykszczesniak.synchub.transform.MappingFailureKind
import com.erykszczesniak.synchub.transform.SchemaDriftDetector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

enum class ProcessOutcome { LOADED, SKIPPED, QUARANTINED }

data class ProcessResult(
    val outcome: ProcessOutcome,
    val loadOutcome: LoadOutcome? = null,
    val driftDetected: Boolean = false,
    val details: String? = null,
)

/**
 * One source record through the whole pipeline, in one transaction:
 * drift check → quarantine or continue → map to canonical → merge into System B → change event →
 * close any open quarantine for the key.
 */
@Service
class RecordProcessor(
    private val driftDetector: SchemaDriftDetector,
    private val driftEvents: DriftEventService,
    private val quarantine: QuarantineService,
    private val recorder: ChangeEventRecorder,
) {
    @Transactional
    fun <C : Any, B : SystemBRecord> process(
        pipeline: FeedPipeline<C, B>,
        record: SourceRecord,
        runId: UUID,
        now: Instant = Instant.now(),
    ): ProcessResult {
        var driftDetected = false
        if (!record.deleted) {
            val report = driftDetector.inspect(pipeline.contract, record.payload)
            if (!report.isEmpty) {
                driftDetected = true
                driftEvents.record(pipeline.name, runId, report.findings, record.sourceUpdatedAt, now)
            }
            if (report.isBlocking) {
                val details = report.findings.joinToString("; ") { it.details }
                quarantine.quarantine(record, runId, QuarantineReason.DRIFT, details, now)
                return ProcessResult(ProcessOutcome.QUARANTINED, driftDetected = true, details = details)
            }
        }

        val change: CanonicalChange<C> =
            try {
                pipeline.toCanonical(record)
            } catch (ex: MappingException) {
                val reason =
                    if (ex.kind ==
                        MappingFailureKind.INVALID
                    ) {
                        QuarantineReason.VALIDATION
                    } else {
                        QuarantineReason.MAPPING
                    }
                quarantine.quarantine(record, runId, reason, ex.message ?: ex.kind.name, now)
                return ProcessResult(ProcessOutcome.QUARANTINED, driftDetected = driftDetected, details = ex.message)
            }

        val result = pipeline.load(change, runId, recorder, now)
        // Any clean pass closes an open quarantine of the same key up to the version System B now holds:
        // also when the load was skipped because that version (or a newer one) was already there.
        quarantine.supersede(pipeline.name, record.businessKey, result.sourceUpdatedAt, now)
        val outcome = if (result.applied) ProcessOutcome.LOADED else ProcessOutcome.SKIPPED
        return ProcessResult(outcome, result.outcome, driftDetected)
    }
}
