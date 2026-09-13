package com.erykszczesniak.synchub.events

import com.erykszczesniak.synchub.repository.OutboxEventEntity
import com.erykszczesniak.synchub.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class PublishSummary(
    val published: Int,
    val failed: Int,
)

/**
 * Drains the outbox: pending rows are sent oldest-first, one at a time, and each is marked published
 * only after the transport acknowledged it. If the process dies between the send and the mark, the
 * row is sent again on the next pass. That is the at-least-once guarantee, and why consumers must be
 * idempotent (deduplicate on `eventId`, or apply only if `version` is newer). Per-key ordering is kept
 * because rows are sent in creation order and keyed by business key.
 */
@Service
class OutboxPublisher(
    private val outbox: OutboxEventRepository,
    private val transport: ChangeEventTransport,
    private val properties: EventsProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${synchub.events.publish-interval:1s}", initialDelayString = "5s")
    fun publishOnSchedule() {
        publishPending()
    }

    /**
     * Publishes everything currently pending. Once an event for a key fails, later events for the same
     * key are left pending in this pass (per-key order is never broken); other keys carry on. Safe to
     * call concurrently with the schedule: passes are serialised and sends are idempotent.
     */
    @Synchronized
    fun publishPending(): PublishSummary {
        var published = 0
        var failed = 0
        val failedKeys = mutableSetOf<String>()
        var batch = outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
        while (batch.isNotEmpty()) {
            for (event in batch) {
                val key = event.feed + "/" + event.businessKey
                if (key in failedKeys) continue
                if (publishOne(event)) {
                    published++
                } else {
                    failed++
                    failedKeys += key
                }
            }
            if (failed > 0) break
            batch = outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
        }
        if (published > 0 || failed > 0) {
            log.info("Outbox pass: {} published, {} failed via {}", published, failed, transport.name)
        }
        return PublishSummary(published, failed)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun publishOne(event: OutboxEventEntity): Boolean {
        val managed = outbox.findById(event.id).orElse(null) ?: return true
        if (managed.publishedAt != null) return true
        managed.attempts += 1
        return try {
            transport.publish(properties.topicFor(managed.feed), managed.businessKey, managed.payload)
            managed.publishedAt = Instant.now()
            managed.lastError = null
            outbox.save(managed)
            true
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            markFailed(managed, ex)
        } catch (ex: java.util.concurrent.ExecutionException) {
            markFailed(managed, ex)
        } catch (ex: java.util.concurrent.TimeoutException) {
            markFailed(managed, ex)
        } catch (ex: org.apache.kafka.common.KafkaException) {
            markFailed(managed, ex)
        } catch (ex: IllegalStateException) {
            markFailed(managed, ex)
        }
    }

    private fun markFailed(
        event: OutboxEventEntity,
        ex: Exception,
    ): Boolean {
        event.lastError = (ex.cause?.message ?: ex.message ?: ex.javaClass.simpleName).take(MAX_ERROR)
        outbox.save(event)
        log.warn("Publishing event {} failed (attempt {}): {}", event.id, event.attempts, event.lastError)
        return false
    }

    fun pending(): Long = outbox.countByPublishedAtIsNull()

    companion object {
        private const val MAX_ERROR = 1000
    }
}
