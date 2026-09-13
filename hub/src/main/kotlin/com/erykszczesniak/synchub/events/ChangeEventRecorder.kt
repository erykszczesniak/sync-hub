package com.erykszczesniak.synchub.events

import com.erykszczesniak.synchub.repository.OutboxEventEntity
import com.erykszczesniak.synchub.repository.OutboxEventRepository
import com.erykszczesniak.synchub.sink.LoadResult
import com.erykszczesniak.synchub.sink.SystemBRecord
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Writes a change event into the outbox for every applied load. Runs inside the caller's transaction
 * (MANDATORY), so the System B write and its event are committed or rolled back together: there is
 * no window in which the destination changed but no event exists, or the reverse.
 */
@Service
class ChangeEventRecorder(
    private val outbox: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun <B : SystemBRecord> record(
        feed: String,
        runId: UUID,
        result: LoadResult<B>,
        now: Instant = Instant.now(),
    ): ChangeEvent? {
        val type = result.outcome.changeType ?: return null
        val event =
            ChangeEvent(
                eventId = UUID.randomUUID(),
                feed = feed,
                type = type,
                businessKey = result.businessKey,
                version = result.version,
                occurredAt = now,
                sourceUpdatedAt = result.sourceUpdatedAt,
                runId = runId,
                before = result.before,
                after = result.after,
            )
        outbox.save(
            OutboxEventEntity(
                id = event.eventId,
                feed = feed,
                businessKey = event.businessKey,
                eventType = type,
                payload = objectMapper.writeValueAsString(event),
                createdAt = now,
            ),
        )
        return event
    }
}
