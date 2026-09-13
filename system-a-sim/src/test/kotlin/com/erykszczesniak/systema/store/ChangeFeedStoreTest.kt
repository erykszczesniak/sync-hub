package com.erykszczesniak.systema.store

import com.erykszczesniak.systema.model.Versioned
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ChangeFeedStoreTest {
    private data class Rec(
        override val key: String,
        override val updatedAt: Instant,
        override val deleted: Boolean = false,
    ) : Versioned

    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val store =
        ChangeFeedStore<Rec>().apply {
            put(Rec("b", t0.plusSeconds(10)))
            put(Rec("a", t0.plusSeconds(10)))
            put(Rec("c", t0.plusSeconds(20)))
            put(Rec("d", t0.plusSeconds(30), deleted = true))
            put(Rec("e", t0))
        }

    @Test
    fun `changes are ordered by updatedAt then key and exclude the lower bound`() {
        val page = store.changes(since = t0, until = null, cursor = null, limit = 10)

        assertThat(page.items.map { it.key }).containsExactly("a", "b", "c", "d")
        assertThat(page.hasMore).isFalse()
        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `cursor pagination continues after the last item even on equal timestamps`() {
        val first = store.changes(since = null, until = null, cursor = null, limit = 2)
        assertThat(first.items.map { it.key }).containsExactly("e", "a")
        assertThat(first.hasMore).isTrue()

        val second = store.changes(since = null, until = null, cursor = first.nextCursor, limit = 2)
        assertThat(second.items.map { it.key }).containsExactly("b", "c")

        val third = store.changes(since = null, until = null, cursor = second.nextCursor, limit = 2)
        assertThat(third.items.map { it.key }).containsExactly("d")
        assertThat(third.hasMore).isFalse()
    }

    @Test
    fun `until is inclusive and tombstones are part of the feed`() {
        val page = store.changes(since = t0.plusSeconds(10), until = t0.plusSeconds(30), cursor = null, limit = 10)

        assertThat(page.items.map { it.key }).containsExactly("c", "d")
        assertThat(page.items.last().deleted).isTrue()
    }

    @Test
    fun `malformed cursor is rejected`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            store.changes(null, null, "not-a-cursor", 10)
        }
    }
}
