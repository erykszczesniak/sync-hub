package com.erykszczesniak.synchub.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * The high-water mark of a feed: the source `updatedAt` up to which the hub has processed changes.
 * [watermarkKey] is the business key of the last record at that timestamp, kept as a tie-breaker so
 * several records sharing one timestamp are never skipped.
 */
@Entity
@Table(name = "feed_watermark")
class FeedWatermarkEntity(
    @Id
    @Column(name = "feed", length = 64)
    val feed: String,
    @Column(name = "watermark_ts", nullable = false)
    var watermarkTs: Instant,
    @Column(name = "watermark_key", length = 128)
    var watermarkKey: String? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
