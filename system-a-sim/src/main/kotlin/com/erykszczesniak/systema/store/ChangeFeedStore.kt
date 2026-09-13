package com.erykszczesniak.systema.store

import com.erykszczesniak.systema.model.Versioned
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class ChangesPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

/**
 * An in-memory collection with a change feed: records ordered by (updatedAt, key) and paged with an
 * opaque cursor, exactly the shape an incremental extractor wants. Timestamps are truncated to
 * milliseconds so ordering is total and stable across JSON round-trips.
 */
class ChangeFeedStore<T : Versioned> {
    private val records = ConcurrentHashMap<String, T>()

    fun get(key: String): T? = records[key]

    fun all(): List<T> = records.values.sortedWith(ORDER)

    fun size(): Int = records.size

    fun put(record: T): T {
        records[record.key] = record
        return record
    }

    fun clear() = records.clear()

    fun latestUpdatedAt(): Instant? = records.values.maxOfOrNull { it.updatedAt }

    fun changes(
        since: Instant?,
        until: Instant?,
        cursor: String?,
        limit: Int,
    ): ChangesPage<T> {
        val after = cursor?.let(Cursor::decode)
        val matching =
            records.values
                .asSequence()
                .filter { since == null || it.updatedAt.isAfter(since) }
                .filter { until == null || !it.updatedAt.isAfter(until) }
                .filter { after == null || isAfterCursor(it, after) }
                .sortedWith(ORDER)
                .take(limit + 1)
                .toList()
        val page = matching.take(limit)
        val hasMore = matching.size > limit
        val next = if (hasMore) Cursor.encode(page.last()) else null
        return ChangesPage(page, next, hasMore)
    }

    private fun isAfterCursor(
        record: T,
        after: Cursor,
    ): Boolean {
        val byTime = record.updatedAt.compareTo(after.updatedAt)
        return byTime > 0 || (byTime == 0 && record.key > after.key)
    }

    data class Cursor(
        val updatedAt: Instant,
        val key: String,
    ) {
        companion object {
            fun encode(record: Versioned): String =
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    "${record.updatedAt.toEpochMilli()}|${record.key}".toByteArray(),
                )

            fun decode(cursor: String): Cursor {
                val decoded = runCatching { String(Base64.getUrlDecoder().decode(cursor)) }.getOrNull()
                val parts = decoded?.split('|', limit = 2)
                require(parts != null && parts.size == 2) { "Malformed cursor" }
                val millis = parts[0].toLongOrNull() ?: throw IllegalArgumentException("Malformed cursor")
                return Cursor(Instant.ofEpochMilli(millis), parts[1])
            }
        }
    }

    companion object {
        private val ORDER = compareBy<Versioned>({ it.updatedAt }, { it.key })

        fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    }
}
