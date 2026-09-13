package com.erykszczesniak.synchub.events

import com.erykszczesniak.synchub.canonical.ChangeType
import com.erykszczesniak.synchub.repository.OutboxEventEntity
import com.erykszczesniak.synchub.repository.OutboxEventRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class OutboxPublisherTest {
    @Autowired
    private lateinit var publisher: OutboxPublisher

    @Autowired
    private lateinit var outbox: OutboxEventRepository

    @MockkBean
    private lateinit var transport: ChangeEventTransport

    @BeforeEach
    fun clean() {
        outbox.deleteAll()
        every { transport.name } returns "fake"
    }

    @Test
    fun `a failed send stays pending, blocks later events of the same key only, and is retried next pass`() {
        val first = pending("cus_1", Instant.parse("2026-03-01T10:00:00Z"))
        val sameKeyLater = pending("cus_1", Instant.parse("2026-03-01T10:00:01Z"))
        val otherKey = pending("cus_2", Instant.parse("2026-03-01T10:00:02Z"))
        every { transport.publish("synchub.changes.customers", "cus_1", any()) } throws
            IllegalStateException("broker down") andThen
            Unit
        every { transport.publish("synchub.changes.customers", "cus_2", any()) } returns Unit

        val failedPass = publisher.publishPending()
        assertThat(failedPass).isEqualTo(PublishSummary(published = 1, failed = 1))
        val stuck = outbox.findById(first.id).orElseThrow()
        assertThat(stuck.publishedAt).isNull()
        assertThat(stuck.attempts).isEqualTo(1)
        assertThat(stuck.lastError).isEqualTo("broker down")
        // Per-key ordering: the later event for cus_1 was not attempted, the unrelated key went through.
        assertThat(outbox.findById(sameKeyLater.id).orElseThrow().attempts).isZero()
        assertThat(outbox.findById(otherKey.id).orElseThrow().publishedAt).isNotNull()

        val recovered = publisher.publishPending()
        assertThat(recovered).isEqualTo(PublishSummary(published = 2, failed = 0))
        assertThat(outbox.countByPublishedAtIsNull()).isZero()
        assertThat(outbox.findById(first.id).orElseThrow().attempts).isEqualTo(2)
        verify(exactly = 3) { transport.publish("synchub.changes.customers", "cus_1", any()) }
    }

    @Test
    fun `already published rows are never sent again`() {
        val done = pending("cus_3", Instant.now()).also { it.publishedAt = Instant.now() }.let(outbox::save)

        assertThat(publisher.publishPending()).isEqualTo(PublishSummary(0, 0))
        verify(exactly = 0) { transport.publish(any(), any(), any()) }
        assertThat(outbox.findById(done.id).orElseThrow().attempts).isZero()
    }

    private fun pending(
        key: String,
        createdAt: Instant,
    ) = outbox.save(
        OutboxEventEntity(UUID.randomUUID(), "customers", key, ChangeType.UPDATED, """{"key":"$key"}""", createdAt),
    )
}
