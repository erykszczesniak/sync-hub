package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.repository.FeedWatermarkEntity
import com.erykszczesniak.synchub.repository.FeedWatermarkRepository
import com.erykszczesniak.synchub.source.ExtractionWindow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

data class Watermark(
    val timestamp: Instant,
    val key: String?,
)

/**
 * Reads and moves per-feed watermarks.
 *
 * Incremental windows start at `watermark - overlap`, not at the watermark itself. Sources rarely
 * guarantee that `updatedAt` is monotonic across concurrent writers, so the hub deliberately re-reads
 * a small overlap; because loading is idempotent (unchanged records are skipped, never duplicated),
 * the overlap costs a few skipped records and buys "no change is ever missed".
 */
@Service
class WatermarkStore(
    private val repository: FeedWatermarkRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun read(feed: String): Watermark? =
        repository.findById(feed).map { Watermark(it.watermarkTs, it.watermarkKey) }.orElse(null)

    fun incrementalWindow(
        feed: String,
        overlap: Duration,
    ): ExtractionWindow = ExtractionWindow(since = read(feed)?.timestamp?.minus(overlap), until = null)

    /** Forgets every watermark. Only for tests and deliberate full re-syncs. */
    @Transactional
    fun reset() = repository.deleteAll()

    /** Moves the watermark forward only; a replay or backfill can never move it back. */
    @Transactional
    fun advance(
        feed: String,
        candidate: Watermark,
    ): Watermark {
        val current = repository.findById(feed).orElse(null)
        if (current == null) {
            repository.save(FeedWatermarkEntity(feed, candidate.timestamp, candidate.key, Instant.now()))
            log.info("Watermark for '{}' initialised at {}", feed, candidate.timestamp)
            return candidate
        }
        if (candidate.timestamp.isAfter(current.watermarkTs)) {
            current.watermarkTs = candidate.timestamp
            current.watermarkKey = candidate.key
            current.updatedAt = Instant.now()
            repository.save(current)
            log.info("Watermark for '{}' advanced to {}", feed, candidate.timestamp)
            return candidate
        }
        return Watermark(current.watermarkTs, current.watermarkKey)
    }
}
