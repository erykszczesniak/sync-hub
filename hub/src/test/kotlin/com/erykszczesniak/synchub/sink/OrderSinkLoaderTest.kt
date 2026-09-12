package com.erykszczesniak.synchub.sink

import com.erykszczesniak.synchub.repository.SystemBOrderRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(OrderSinkLoader::class)
class OrderSinkLoaderTest {
    @Autowired
    private lateinit var loader: OrderSinkLoader

    @Autowired
    private lateinit var repository: SystemBOrderRepository

    private val t0 = Instant.parse("2026-03-01T10:00:00Z")

    @Test
    fun `orders follow the same merge rules`() {
        assertThat(loader.load("ord_1", t0, row(t0, "PEN")).outcome).isEqualTo(LoadOutcome.CREATED)
        assertThat(loader.load("ord_1", t0, row(t0, "PEN")).outcome).isEqualTo(LoadOutcome.SKIPPED_UNCHANGED)
        assertThat(
            loader.load("ord_1", t0.plusSeconds(5), row(t0.plusSeconds(5), "PAI")).outcome,
        ).isEqualTo(LoadOutcome.UPDATED)
        assertThat(
            loader.load("ord_1", t0.plusSeconds(1), row(t0.plusSeconds(1), "CAN")).outcome,
        ).isEqualTo(LoadOutcome.SKIPPED_STALE)
        assertThat(loader.load("ord_1", t0.plusSeconds(9), null).outcome).isEqualTo(LoadOutcome.DELETED)

        val stored = repository.findByBusinessKey("ord_1")!!
        assertThat(stored.stateCode).isEqualTo("PAI")
        assertThat(stored.version).isEqualTo(3)
        assertThat(stored.deletedAt).isNotNull()
        assertThat(repository.count()).isEqualTo(1)
    }

    private fun row(
        updatedAt: Instant,
        state: String,
    ) = SystemBOrderRecord(
        orderKey = "ord_1",
        customerKey = "cus_1",
        stateCode = state,
        totalMinor = 1000,
        currency = "EUR",
        lineCount = 1,
        linesJson = """[{"sku":"S","qty":1,"unitMinor":1000}]""",
        placedAt = t0.minusSeconds(60),
        sourceUpdatedAt = updatedAt,
    )
}
