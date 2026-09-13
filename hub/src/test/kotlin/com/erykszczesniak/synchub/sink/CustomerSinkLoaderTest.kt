package com.erykszczesniak.synchub.sink

import com.erykszczesniak.synchub.repository.SystemBCustomerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.random.Random

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(CustomerSinkLoader::class)
class CustomerSinkLoaderTest {
    @Autowired
    private lateinit var loader: CustomerSinkLoader

    @Autowired
    private lateinit var repository: SystemBCustomerRepository

    private val t0 = Instant.parse("2026-03-01T10:00:00Z")

    @Test
    fun `first load creates, an identical replay is skipped without a write that matters`() {
        val created = loader.load("cus_1", t0, row(t0))
        assertThat(created.outcome).isEqualTo(LoadOutcome.CREATED)
        assertThat(created.before).isNull()
        assertThat(created.after!!.fullName).isEqualTo("Anna Nowak")
        assertThat(created.version).isEqualTo(1)

        val replay = loader.load("cus_1", t0, row(t0))
        assertThat(replay.outcome).isEqualTo(LoadOutcome.SKIPPED_UNCHANGED)
        assertThat(replay.applied).isFalse()
        assertThat(repository.count()).isEqualTo(1)
        assertThat(repository.findByBusinessKey("cus_1")!!.version).isEqualTo(1)
    }

    @Test
    fun `a newer version updates with before and after, an older one is stale and ignored`() {
        loader.load("cus_1", t0, row(t0))

        val updated = loader.load("cus_1", t0.plusSeconds(60), row(t0.plusSeconds(60), tier = 3))
        assertThat(updated.outcome).isEqualTo(LoadOutcome.UPDATED)
        assertThat(updated.before!!.tierRank).isEqualTo(2)
        assertThat(updated.after!!.tierRank).isEqualTo(3)
        assertThat(updated.version).isEqualTo(2)

        val late = loader.load("cus_1", t0.plusSeconds(30), row(t0.plusSeconds(30), tier = 1))
        assertThat(late.outcome).isEqualTo(LoadOutcome.SKIPPED_STALE)
        assertThat(repository.findByBusinessKey("cus_1")!!.tierRank).isEqualTo(3)
        assertThat(repository.findByBusinessKey("cus_1")!!.sourceUpdatedAt).isEqualTo(t0.plusSeconds(60))
    }

    @Test
    fun `an unchanged re-read with a newer clock nudges the clock but stays silent`() {
        loader.load("cus_1", t0, row(t0))

        val touched = loader.load("cus_1", t0.plusSeconds(10), row(t0.plusSeconds(10)))

        assertThat(touched.outcome).isEqualTo(LoadOutcome.SKIPPED_UNCHANGED)
        assertThat(repository.findByBusinessKey("cus_1")!!.sourceUpdatedAt).isEqualTo(t0.plusSeconds(10))
        assertThat(repository.findByBusinessKey("cus_1")!!.version).isEqualTo(1)
    }

    @Test
    fun `deletes are soft, idempotent, late-arrival safe, and a later upsert resurrects`() {
        loader.load("cus_1", t0, row(t0))

        val deleted = loader.load("cus_1", t0.plusSeconds(60), null)
        assertThat(deleted.outcome).isEqualTo(LoadOutcome.DELETED)
        assertThat(deleted.before!!.customerKey).isEqualTo("cus_1")
        assertThat(repository.findByBusinessKey("cus_1")!!.deletedAt).isNotNull()
        assertThat(repository.countByDeletedAtIsNull()).isZero()

        assertThat(loader.load("cus_1", t0.plusSeconds(60), null).outcome).isEqualTo(LoadOutcome.SKIPPED_UNCHANGED)
        assertThat(
            loader.load("cus_1", t0.plusSeconds(30), row(t0.plusSeconds(30))).outcome,
        ).isEqualTo(LoadOutcome.SKIPPED_STALE)
        assertThat(loader.load("cus_9", t0, null).outcome).isEqualTo(LoadOutcome.SKIPPED_UNCHANGED)

        val back = loader.load("cus_1", t0.plusSeconds(120), row(t0.plusSeconds(120)))
        assertThat(back.outcome).isEqualTo(LoadOutcome.CREATED)
        assertThat(back.before).isNull()
        assertThat(repository.findByBusinessKey("cus_1")!!.deletedAt).isNull()
        assertThat(repository.count()).isEqualTo(1)
    }

    @Test
    fun `a backfill delivered in any order converges to the newest state with no duplicates`() {
        val versions =
            (0 until 10).map { i ->
                Triple(
                    "cus_1",
                    t0.plusSeconds(i * 60L),
                    row(
                        t0.plusSeconds(i * 60L),
                        tier =
                            (i % 4) + 1,
                    ),
                )
            }

        versions.shuffled(Random(7)).forEach { (key, ts, record) -> loader.load(key, ts, record) }
        versions.forEach { (key, ts, record) -> loader.load(key, ts, record) }

        assertThat(repository.count()).isEqualTo(1)
        val stored = repository.findByBusinessKey("cus_1")!!
        assertThat(stored.sourceUpdatedAt).isEqualTo(t0.plusSeconds(9 * 60L))
        assertThat(stored.tierRank).isEqualTo((9 % 4) + 1)
    }

    private fun row(
        updatedAt: Instant,
        tier: Int = 2,
    ) = SystemBCustomerRecord(
        customerKey = "cus_1",
        emailAddress = "anna@example.com",
        fullName = "Anna Nowak",
        givenName = "Anna",
        familyName = "Nowak",
        lifecycleCode = "A",
        tierRank = tier,
        addressLine = "1 Street",
        city = "Warsaw",
        postalCode = "00-001",
        countryCode = "PL",
        tagsCsv = "vip",
        sourceCreatedAt = t0.minusSeconds(3600),
        sourceUpdatedAt = updatedAt,
    )
}
