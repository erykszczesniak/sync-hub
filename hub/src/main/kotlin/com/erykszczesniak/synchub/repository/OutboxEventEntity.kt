package com.erykszczesniak.synchub.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID
import com.erykszczesniak.synchub.canonical.ChangeType as EventType

@Entity
@Table(name = "change_event_outbox")
class OutboxEventEntity(
    @Id
    @Column(name = "id")
    val id: UUID,
    @Column(name = "feed", nullable = false, length = 64)
    val feed: String,
    @Column(name = "business_key", nullable = false, length = 128)
    val businessKey: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    val eventType: EventType,
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "published_at")
    var publishedAt: Instant? = null,
    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,
    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,
)

interface OutboxEventRepository : JpaRepository<OutboxEventEntity, UUID> {
    fun findTop100ByPublishedAtIsNullOrderByCreatedAtAsc(): List<OutboxEventEntity>

    fun countByPublishedAtIsNull(): Long

    fun countByPublishedAtIsNotNull(): Long

    fun findByFeedAndBusinessKeyOrderByCreatedAtAsc(
        feed: String,
        businessKey: String,
    ): List<OutboxEventEntity>
}
